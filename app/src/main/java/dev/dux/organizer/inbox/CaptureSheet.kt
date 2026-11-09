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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.tasks.DateField
import dev.dux.organizer.tasks.PriorityField
import dev.dux.organizer.tasks.ProjectField
import dev.dux.organizer.tasks.ReminderField
import dev.dux.organizer.tasks.RepeatField
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import dev.dux.organizer.data.Project
import dev.dux.organizer.ui.ReminderDialogs
import dev.dux.organizer.ui.reminderLabel
import dev.dux.organizer.ui.rememberToday
import java.time.LocalDate
import java.time.LocalDateTime
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import dev.dux.organizer.ui.appLocale
import dev.dux.organizer.ui.inlineLabel

/** What can be set while capturing, besides the title and notes. All optional. */
data class CaptureDetails(
    val due: LocalDate? = null,
    val remindAt: LocalDateTime? = null,
    val projectId: String? = null,
    val scheduled: LocalDate? = null,
    val priority: Priority = Priority.NONE,
    val recurrence: Recurrence? = null,
) {
    /** Anything planned sends the task straight to "Por hacer" (the same rule as the repository). */
    val sortsTheTask get() = this != CaptureDetails()
}

/**
 * Quick capture: required title, optional notes, shortcut chips (Hoy, Mañana, Recordatorio) and, under
 * "Más opciones", the same planning rows as the task detail, so a task can be fully set up here and both
 * screens are learned once. [onSave] is only called with a valid title. [projects]: null hides the project
 * row (capturing inside a project).
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
    var scheduled by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var priority by rememberSaveable { mutableStateOf(Priority.NONE) }
    // Recurrence is not Parcelable: saved as its rule text.
    var recurrenceRule by rememberSaveable { mutableStateOf<String?>(null) }
    val recurrence = recurrenceRule?.let(Recurrence::parse)
    var more by rememberSaveable { mutableStateOf(false) }
    var pickingReminder by rememberSaveable { mutableStateOf(false) }
    var reminderInPast by rememberSaveable { mutableStateOf(false) }
    val today by rememberToday()
    val focus = remember { FocusRequester() }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // "mañana a las 10" typed or dictated in the title: used unless the user dismissed it or set that field by hand.
    val spotted = remember(title, today, due) { spotWhen(title, LocalDateTime.now(), due) }
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    val spot = spotted?.takeIf { "${it.due}|${it.remindAt}" != dismissed }
    val finalTitle = spot?.title ?: title
    val finalRemind = remindAt ?: spot?.remindAt
    // A reminder without a date makes its day the date, as when asking Jarvis by voice.
    val details = CaptureDetails(due ?: spot?.due ?: finalRemind?.toLocalDate(), finalRemind, projectId, scheduled, priority, recurrence)
    val save = { if (cleanTitle(finalTitle) == null) showError = true else onSave(finalTitle, notes, details) }
    // The system dictation screen: no microphone permission needed in Jarvis. Appended to what is already typed.
    var voiceMissing by rememberSaveable { mutableStateOf(false) }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            title = if (title.isBlank()) spoken.replaceFirstChar { it.uppercase() } else "${title.trimEnd()} $spoken"
            showError = false
        }
    }
    val locale = appLocale()
    val dictatePrompt = stringResource(R.string.capture_title)
    val dictate = {
        try {
            listen.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, dictatePrompt),
            )
        } catch (e: ActivityNotFoundException) {
            voiceMissing = true
        }
    }

    // Title and notes without boxes, as in the task detail.
    val plain = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val titleLabel = heading ?: stringResource(R.string.capture_title)
            TextField(
                value = title,
                onValueChange = { title = it; showError = false },
                placeholder = { Text(titleLabel) },
                isError = showError,
                supportingText = when {
                    showError -> ({ Text(stringResource(R.string.title_invalid)) })
                    voiceMissing -> ({ Text(stringResource(R.string.voice_missing)) })
                    else -> null
                },
                trailingIcon = { IconButton(onClick = dictate) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.dictate)) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = plain,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = titleLabel },
            )
            val notesLabel = stringResource(R.string.notes_optional)
            TextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(notesLabel) },
                colors = plain,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = notesLabel },
            )
            // What was understood from the title, before saving; tap to ignore it.
            AnimatedVisibility(spot != null) {
                val shown = spot ?: spotted
                if (shown != null) {
                    val label = shown.remindAt?.let { reminderLabel(it, today) } ?: shown.due?.inlineLabel(today).orEmpty().replaceFirstChar { it.uppercase() }
                    val description = stringResource(R.string.spotted_description, label, shown.title)
                    InputChip(
                        selected = true,
                        onClick = { dismissed = "${shown.due}|${shown.remindAt}" },
                        label = { Text(stringResource(R.string.spotted, label)) },
                        leadingIcon = { Icon(painterResource(if (shown.remindAt != null) R.drawable.ic_bell else R.drawable.ic_today), contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) },
                        modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = description },
                    )
                }
            }
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tomorrow = today.plusDays(1)
                Chip(stringResource(R.string.today), due == today, R.drawable.ic_today) { due = if (due == today) null else today }
                Chip(stringResource(R.string.tomorrow), due == tomorrow, R.drawable.ic_today) { due = if (due == tomorrow) null else tomorrow }
                Chip(remindAt?.let { reminderLabel(it, today) } ?: stringResource(R.string.reminder), remindAt != null, R.drawable.ic_bell) {
                    reminderInPast = false
                    pickingReminder = true
                    // Asked here, the first time it is needed, as in the task detail.
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (reminderInPast) {
                Text(stringResource(R.string.reminder_in_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            TextButton(onClick = { more = !more }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(stringResource(if (more) R.string.fewer_options else R.string.more_options))
                val turn by animateFloatAsState(if (more) 180f else 0f, label = "chevron")
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, Modifier.padding(start = 4.dp).rotate(90f + turn))
            }
            AnimatedVisibility(more) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        DateField(R.drawable.ic_today, R.string.due_date, due) { due = it }
                        DateField(R.drawable.ic_event, R.string.scheduled_date, scheduled) { scheduled = it }
                        PriorityField(priority) { priority = it }
                        if (projects != null) ProjectField(projects, projectId) { projectId = it }
                        ReminderField(remindAt) { remindAt = it }
                        RepeatField(recurrence, due ?: scheduled) { recurrenceRule = it?.format() }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Says where the task will land before saving, so "Por ordenar" and "Por hacer" are never a surprise.
                val sorted = details.sortsTheTask
                Box(Modifier.size(8.dp).background(if (sorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, CircleShape))
                Text(
                    stringResource(R.string.goes_to, stringResource(if (sorted) R.string.status_todo else R.string.status_inbox)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Button(onClick = save, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.save), Modifier.padding(start = 8.dp))
                }
            }
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
