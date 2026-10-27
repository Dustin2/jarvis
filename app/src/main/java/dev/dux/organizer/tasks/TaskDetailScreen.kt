package dev.dux.organizer.tasks

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Project
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.ui.Choice
import dev.dux.organizer.ui.SPANISH
import dev.dux.organizer.ui.SavedIndicator
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Changes save instantly: there is no Save button and no way to lose an edit. */
@Composable
fun TaskDetailScreen(vm: TaskDetailViewModel, onBack: () -> Unit) {
    val task by vm.task.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SavedIndicator(task?.updatedAt) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = task != null) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        task?.let { TaskForm(it, vm, Modifier.padding(padding)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TaskForm(task: Task, vm: TaskDetailViewModel, modifier: Modifier) {
    // Local text state: initialized once and never overwritten by what comes back from the database.
    var title by rememberSaveable { mutableStateOf(task.title) }
    var notes by rememberSaveable { mutableStateOf(task.notes.orEmpty()) }
    var titleError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; vm.rename(it) { ok -> titleError = !ok } },
            label = { Text(stringResource(R.string.title)) },
            isError = titleError,
            supportingText = if (titleError) ({ Text(stringResource(R.string.title_invalid)) }) else null,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it; vm.setNotes(it) },
            label = { Text(stringResource(R.string.notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Choice(R.string.status, TaskStatus.entries, task.status, { it.label }, vm::setStatus)
        Choice(R.string.priority, Priority.entries, task.priority, { it.label }, vm::setPriority)
        DateField(R.string.due_date, task.dueDate, vm::setDueDate)
        DateField(R.string.scheduled_date, task.scheduledDate, vm::setScheduledDate)
        val projects by vm.projects.collectAsStateWithLifecycle()
        ProjectField(projects.orEmpty(), task.projectId, vm::setProject)
        ReminderField(task.reminderAt, vm::setReminder)
        RepeatField(task, vm::setRecurrence)
    }
}

/**
 * Label and button on one line; with large fonts the button moves down instead of breaking the label.
 * The button is announced with its field ("Fecha límite: Sin fecha"), not only its value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Field(title: Int, value: String, onClick: () -> Unit, menu: @Composable () -> Unit = {}) {
    val label = stringResource(title)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
        Box {
            OutlinedButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "$label: $value" }) { Text(value) }
            menu()
        }
    }
}

@Composable
private fun RepeatField(task: Task, onChange: (Recurrence?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Field(R.string.repeat, task.recurrence.describe(), onClick = { editing = true })
    if (editing) {
        RepeatDialog(
            initial = task.recurrence,
            anchor = task.dueDate ?: task.scheduledDate ?: LocalDate.now(),
            onDismiss = { editing = false },
            onSave = { editing = false; onChange(it) },
        )
    }
}

@Composable
private fun DateField(title: Int, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today by rememberToday()
    Field(title, date?.label(today) ?: stringResource(R.string.no_date), onClick = { picking = true })
    if (picking) {
        DatePickDialog(date ?: today, R.string.remove_date, canRemove = date != null, onDismiss = { picking = false }) {
            picking = false
            onChange(it)
        }
    }
}

/** [onResult] receives the chosen date, or null if "remove" was pressed. */
// DatePicker is still marked experimental, but it is the official M3 picker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, removeLabel: Int, canRemove: Boolean, onDismiss: () -> Unit, onResult: (LocalDate?) -> Unit) {
    // DatePicker works in UTC milliseconds at midnight; always converting in UTC avoids the "one day earlier" bug.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onResult(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.accept)) }
        },
        dismissButton = {
            TextButton(onClick = { onResult(null) }, enabled = canRemove) { Text(stringResource(removeLabel)) }
        },
    ) { DatePicker(state) }
}

/**
 * Date, then time. Asks for the notification permission here, the first time it is needed,
 * and not when the app opens.
 */
// TimePicker is still marked experimental, but it is the official M3 picker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderField(at: LocalDateTime?, onChange: (LocalDateTime?) -> Unit) {
    val context = LocalContext.current
    val today by rememberToday()
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var inPast by rememberSaveable { mutableStateOf(false) }
    var notificationsOff by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOff = !granted
    }

    // Same 12/24-hour format the user chose on the phone (and the picker uses).
    val timeFormat = remember { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", SPANISH) }
    val label = at?.let { "${it.toLocalDate().label(today)}, ${it.toLocalTime().format(timeFormat)}" } ?: stringResource(R.string.no_reminder)
    Field(R.string.reminder, label, onClick = {
        inPast = false
        pickingDate = true
        if (notificationsOff) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    })
    if (inPast) Text(stringResource(R.string.reminder_in_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (at != null && notificationsOff) {
        Text(stringResource(R.string.notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (pickingDate) {
        DatePickDialog(at?.toLocalDate() ?: today, R.string.remove_reminder, canRemove = at != null, onDismiss = { pickingDate = false }) { date ->
            pickingDate = false
            if (date == null) onChange(null) else pickedDate = date
        }
    }
    pickedDate?.let { date ->
        val initial = at?.toLocalTime() ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickedDate = null },
            title = { Text(stringResource(R.string.pick_time)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = null
                    val chosen = date.atTime(state.hour, state.minute)
                    if (chosen <= LocalDateTime.now()) inPast = true else onChange(chosen)
                }) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ProjectField(projects: List<Project>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = when (selectedId) {
        null -> stringResource(R.string.no_project)
        // An archived project is not listed, but the task is still linked to it.
        else -> projects.find { it.id == selectedId }?.name ?: stringResource(R.string.project_status_archived)
    }
    Field(R.string.project, name, onClick = { open = true }) {
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_project)) }, onClick = { open = false; onSelect(null) })
            projects.forEach { project ->
                DropdownMenuItem(text = { Text(project.name) }, onClick = { open = false; onSelect(project.id) })
            }
        }
    }
}
