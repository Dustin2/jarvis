package dev.dux.organizer.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.data.cleanTitle
import dev.dux.organizer.ui.Drafts
import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import dev.dux.organizer.data.Project
import dev.dux.organizer.ui.DatePickDialog
import dev.dux.organizer.ui.ReminderDialogs
import dev.dux.organizer.ui.appLocale
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What can be set while capturing, besides the title and notes. All optional. */
data class CaptureDetails(val due: LocalDate? = null, val remindAt: LocalDateTime? = null, val projectId: String? = null)

/**
 * Quick capture: required title, optional notes, and chips for the usual details (Hoy, Mañana, a date, a
 * reminder, a project), so a task does not need a second trip to its detail screen. [onSave] is only
 * called with a valid title. [projects]: null hides the project chip (capturing inside a project).
 */
// ModalBottomSheet has been marked experimental for years, but it is the official M3 pattern for this.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
/** [heading] replaces "¿Qué tienes pendiente?" (e.g. "Nueva tarea en «Pro-Hielo»"); [draftKey] keeps separate drafts per place. */
fun CaptureSheet(
    drafts: Drafts,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, details: CaptureDetails) -> Unit,
    heading: String? = null,
    draftKey: String = "capture",
    projects: List<Project>? = null,
) {
    val context = LocalContext.current
    // Kept by the screen: closing the sheet by accident does not lose what was typed.
    var title by drafts.field("$draftKey.title")
    var notes by drafts.field("$draftKey.notes")
    var showError by rememberSaveable { mutableStateOf(false) }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var remindAt by rememberSaveable { mutableStateOf<LocalDateTime?>(null) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingReminder by rememberSaveable { mutableStateOf(false) }
    var reminderInPast by rememberSaveable { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val today by rememberToday()
    val focus = remember { FocusRequester() }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val save = {
        // A reminder without a date makes its day the date, as when asking Jarvis by voice.
        if (cleanTitle(title) == null) showError = true else onSave(title, notes, CaptureDetails(due ?: remindAt?.toLocalDate(), remindAt, projectId))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(heading ?: stringResource(R.string.capture_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; showError = false },
                label = { Text(stringResource(R.string.title)) },
                isError = showError,
                supportingText = if (showError) ({ Text(stringResource(R.string.title_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tomorrow = today.plusDays(1)
                Chip(stringResource(R.string.today), due == today, R.drawable.ic_today) { due = if (due == today) null else today }
                Chip(stringResource(R.string.tomorrow), due == tomorrow, R.drawable.ic_today) { due = if (due == tomorrow) null else tomorrow }
                val otherDay = due?.takeIf { it != today && it != tomorrow }
                Chip(otherDay?.label(today) ?: stringResource(R.string.date), otherDay != null, R.drawable.ic_event) { pickingDate = true }
                Chip(remindAt?.let { reminderLabel(it, today) } ?: stringResource(R.string.reminder), remindAt != null, R.drawable.ic_bell) {
                    reminderInPast = false
                    pickingReminder = true
                    // Asked here, the first time it is needed, as in the task detail.
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (projects != null) Box {
                    val name = projects.find { it.id == projectId }?.name
                    Chip(name ?: stringResource(R.string.project), name != null, R.drawable.ic_projects) { projectMenu = true }
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.no_project)) }, onClick = { projectMenu = false; projectId = null })
                        projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { projectMenu = false; projectId = p.id }) }
                    }
                }
            }
            if (reminderInPast) Text(stringResource(R.string.reminder_in_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes_optional)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = save, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.save)) }
        }
    }
    if (pickingDate) {
        DatePickDialog(due ?: today, R.string.remove_date, canRemove = due != null, onDismiss = { pickingDate = false }) {
            pickingDate = false
            due = it
        }
    }
    if (pickingReminder) {
        ReminderDialogs(remindAt, today, onDismiss = { pickingReminder = false }, onPast = { pickingReminder = false; reminderInPast = true }) {
            pickingReminder = false
            remindAt = it
        }
    }
    // Open the keyboard right away: capturing should take seconds.
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun Chip(text: String, selected: Boolean, icon: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

/** "Mañana, 9:00", in the phone's 12/24-hour format. */
@Composable
private fun reminderLabel(at: LocalDateTime, today: LocalDate): String {
    val context = LocalContext.current
    val locale = appLocale()
    val time = remember(locale) { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", locale) }
    return "${at.toLocalDate().label(today)}, ${at.toLocalTime().format(time)}"
}
