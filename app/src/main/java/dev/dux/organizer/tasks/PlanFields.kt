package dev.dux.organizer.tasks

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.dux.organizer.R
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Project
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.ui.DatePickDialog
import dev.dux.organizer.ui.PlanRow
import dev.dux.organizer.ui.ReminderDialogs
import dev.dux.organizer.ui.appLocale
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// The planning rows of a task, shared by its detail screen and the capture sheet so both look and work the same.

@Composable
internal fun PriorityField(priority: Priority, onSelect: (Priority) -> Unit) {
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
internal fun RepeatField(recurrence: Recurrence?, anchor: LocalDate?, onChange: (Recurrence?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    PlanRow(R.drawable.ic_repeat, R.string.repeat, recurrence.describe(), isSet = recurrence != null, onClick = { editing = true })
    if (editing) {
        RepeatDialog(
            initial = recurrence,
            anchor = anchor ?: LocalDate.now(),
            onDismiss = { editing = false },
            onSave = { editing = false; onChange(it) },
        )
    }
}

@Composable
internal fun DateField(@DrawableRes icon: Int, @StringRes title: Int, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
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
@Composable
internal fun ReminderField(at: LocalDateTime?, onChange: (LocalDateTime?) -> Unit) {
    val context = LocalContext.current
    val today by rememberToday()
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
        ReminderDialogs(at, today, onDismiss = { pickingDate = false }, onPast = { pickingDate = false; inPast = true }) {
            pickingDate = false
            onChange(it)
        }
    }
}

@Composable
internal fun ProjectField(projects: List<Project>, selectedId: String?, onSelect: (String?) -> Unit) {
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
