package dev.dux.organizer.tasks

import dev.dux.organizer.data.TrashRepository
import dev.dux.organizer.ui.appLocale
import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.ui.DatePickDialog
import dev.dux.organizer.ui.PlanRow
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Project
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.ui.SavedIndicator
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Changes save instantly: there is no Save button and no way to lose an edit. */
@Composable
fun TaskDetailScreen(vm: TaskDetailViewModel, onBack: () -> Unit) {
    val task by vm.task.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

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
                    // Status changes other than completing, and deleting, live here (docs/design/task-detail.md).
                    task?.let { current ->
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options))
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (current.status == TaskStatus.INBOX) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_todo)) }, onClick = { menuOpen = false; vm.setStatus(TaskStatus.TODO) })
                                }
                                if (current.status == TaskStatus.TODO) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.back_to_inbox)) }, onClick = { menuOpen = false; vm.setStatus(TaskStatus.INBOX) })
                                }
                                if (current.status in OPEN_STATUSES) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.cancel_task)) }, onClick = { menuOpen = false; vm.setStatus(TaskStatus.CANCELLED) })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            task?.let { current ->
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (current.status in OPEN_STATUSES) {
                        Button(onClick = { vm.setStatus(TaskStatus.DONE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                            Text(stringResource(R.string.complete), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        OutlinedButton(onClick = { vm.setStatus(TaskStatus.TODO) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.reopen), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        task?.let { TaskForm(it, vm, Modifier.padding(padding)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.task_to_trash_body, TrashRepository.KEEP_DAYS.toInt(), TrashRepository.KEEP_DAYS)) },
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

    // Title and notes without boxes: the planning options below are the only framed block.
    val plain = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val titleLabel = stringResource(R.string.title)
    val notesLabel = stringResource(R.string.notes)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = title,
            onValueChange = { title = it; vm.rename(it) { ok -> titleError = !ok } },
            placeholder = { Text(titleLabel) },
            isError = titleError,
            supportingText = if (titleError) ({ Text(stringResource(R.string.title_invalid)) }) else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            colors = plain,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = titleLabel },
        )
        if (task.status !in OPEN_STATUSES) {
            Text(
                stringResource(task.status.label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        TextField(
            value = notes,
            onValueChange = { notes = it; vm.setNotes(it) },
            placeholder = { Text(stringResource(R.string.add_notes)) },
            colors = plain,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = notesLabel },
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(bottom = 16.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                DateField(R.drawable.ic_today, R.string.due_date, task.dueDate, vm::setDueDate)
                DateField(R.drawable.ic_event, R.string.scheduled_date, task.scheduledDate, vm::setScheduledDate)
                PriorityField(task.priority, vm::setPriority)
                val projects by vm.projects.collectAsStateWithLifecycle()
                ProjectField(projects.orEmpty(), task.projectId, vm::setProject)
                ReminderField(task.reminderAt, vm::setReminder)
                RepeatField(task, vm::setRecurrence)
            }
        }
    }
}

private val OPEN_STATUSES = setOf(TaskStatus.INBOX, TaskStatus.TODO)

@Composable
private fun PriorityField(priority: Priority, onSelect: (Priority) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PlanRow(R.drawable.ic_flag, R.string.priority, stringResource(priority.label), isSet = priority != Priority.NONE, onClick = { open = true }) {
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Priority.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun RepeatField(task: Task, onChange: (Recurrence?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    PlanRow(R.drawable.ic_repeat, R.string.repeat, task.recurrence.describe(), isSet = task.recurrence != null, onClick = { editing = true })
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
private fun DateField(@DrawableRes icon: Int, @StringRes title: Int, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today by rememberToday()
    PlanRow(icon, title, date?.label(today) ?: stringResource(R.string.no_date), isSet = date != null, onClick = { picking = true })
    if (picking) {
        DatePickDialog(date ?: today, R.string.remove_date, canRemove = date != null, onDismiss = { picking = false }) {
            picking = false
            onChange(it)
        }
    }
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
    val locale = appLocale()
    val timeFormat = remember(locale) { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", locale) }
    val label = at?.let { "${it.toLocalDate().label(today)}, ${it.toLocalTime().format(timeFormat)}" } ?: stringResource(R.string.no_reminder)
    PlanRow(R.drawable.ic_bell, R.string.reminder, label, isSet = at != null, onClick = {
        inPast = false
        pickingDate = true
        if (notificationsOff) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    })
    val warning = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)
    if (inPast) Text(stringResource(R.string.reminder_in_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = warning)
    if (at != null && notificationsOff) {
        Text(stringResource(R.string.notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = warning)
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
    PlanRow(R.drawable.ic_projects, R.string.project, name, isSet = selectedId != null, onClick = { open = true }) {
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_project)) }, onClick = { open = false; onSelect(null) })
            projects.forEach { project ->
                DropdownMenuItem(text = { Text(project.name) }, onClick = { open = false; onSelect(project.id) })
            }
        }
    }
}
