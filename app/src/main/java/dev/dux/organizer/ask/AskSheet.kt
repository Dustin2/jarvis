package dev.dux.organizer.ask

import java.time.format.TextStyle
import dev.dux.organizer.data.Frequency
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.data.Priority
import java.time.format.FormatStyle
import java.time.LocalDateTime
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.util.Locale
import dev.dux.organizer.ui.appLocale
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.data.MoneyKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How the sheet was opened from outside the app: listening right away, or asking for the day (morning brief). */
enum class AskStart { LISTEN, BRIEF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSheet(
    ask: AskJarvis,
    data: suspend () -> AskData,
    onAction: suspend (JarvisAction) -> (suspend () -> Unit)?,
    start: AskStart? = null,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    /** The question being answered; the field is cleared on send, ready for a follow-up. */
    var asked by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    /** Earlier exchanges in this sheet, oldest first; the latest few go back to the model as context. */
    val turns = remember { mutableStateListOf<Turn>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    val thinking = stringResource(R.string.ask_thinking)
    val failed = stringResource(R.string.ask_failed)
    val voiceMissing = stringResource(R.string.ask_voice_missing)
    val speaker = rememberSpeaker(appLocale())
    /** An action waiting for the user to confirm it; nothing changes until "Hacer". */
    var proposal by remember { mutableStateOf<JarvisAction?>(null) }
    /** Undo for the action just done; cleared by the next question. */
    var undo by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val undone = stringResource(R.string.action_undone)
    /** This turn started by voice, so what Jarvis says is read aloud too. */
    var spoken by remember { mutableStateOf(false) }
    fun say(text: String) {
        answer = text
        if (spoken) speaker.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
    }
    suspend fun answerQuestion(q: String, facts: String) {
        var first = true
        ask.ask(q, facts, turns.takeLast(MAX_CONTEXT_TURNS)).collect { answer = if (first) it else answer + it; first = false }
        if (spoken) speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "answer")
    }
    fun launch(block: suspend () -> Unit) {
        job = scope.launch { runCatching { block() }.onFailure { answer = failed } }
    }
    fun send(byVoice: Boolean = false) {
        if (question.isBlank() || job?.isActive == true) return
        // The previous exchange becomes context, unless it never got a real answer.
        if (asked.isNotEmpty() && answer.isNotEmpty() && answer != thinking && answer != failed) turns += Turn(asked, answer)
        asked = question.trim()
        question = ""
        speaker.stop()
        proposal = null
        undo = null
        spoken = byVoice
        answer = thinking
        launch {
            val q = asked
            val d = data()
            // A question mark means a question: no need to look for an action.
            val action = if (q.endsWith("?")) null
            else parseAction(ask.proposeAction(q, d.facts(), turns.takeLast(MAX_CONTEXT_TURNS)), q, d.open, d.lists, d.today, d.categories, LocalDateTime.now())
            if (action == null) answerQuestion(q, d.facts())
            else {
                proposal = action
                say(actionTexts(action, resources).first)
            }
        }
    }
    fun confirm(action: JarvisAction) {
        proposal = null
        launch {
            undo = onAction(action)
            say(if (undo != null) actionTexts(action, resources).second else failed)
        }
    }
    fun notAnAction() {
        proposal = null
        answer = thinking
        launch { answerQuestion(asked, data().facts()) }
    }
    // The system dictation screen: no microphone permission needed in Jarvis.
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            question = it
            send(byVoice = true)
        }
    }
    val prompt = stringResource(R.string.ask_jarvis)
    val locale = appLocale()
    fun dictate() {
        speaker.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            listen.launch(intent)
        } catch (e: ActivityNotFoundException) {
            answer = voiceMissing
        }
    }
    // Widget or shortcut: straight to dictation. Morning brief: ask for the day and read it aloud. Otherwise ready to type.
    val briefQuestion = stringResource(R.string.brief_question)
    LaunchedEffect(Unit) {
        if (!ask.installed) return@LaunchedEffect
        when (start) {
            AskStart.LISTEN -> dictate()
            AskStart.BRIEF -> {
                question = briefQuestion
                send(byVoice = true)
            }
            null -> focus.requestFocus()
        }
    }
    // A failed warm-up is not shown: the first question loads the model again and reports the error then.
    LaunchedEffect(Unit) { runCatching { ask.warmUp() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.ask_jarvis), style = MaterialTheme.typography.titleLarge)
            if (!ask.installed) {
                Text(stringResource(R.string.ask_no_model), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { onDismiss(); onOpenSettings() }) { Text(stringResource(R.string.settings)) }
                return@Column
            }
            OutlinedTextField(
                question, { question = it },
                Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.ask_hint)) },
                // Like messaging apps: microphone while empty, send once there is text.
                trailingIcon = {
                    if (question.isBlank()) IconButton(::dictate) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.ask_voice)) }
                    else IconButton({ send() }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.ask_send)) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            if (asked.isNotEmpty()) Text(asked, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (answer.isNotEmpty()) Text(answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            undo?.let { revert ->
                TextButton(onClick = {
                    undo = null
                    launch { revert(); say(undone) }
                }) { Text(stringResource(R.string.undo)) }
            }
            proposal?.let { action ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirm(action) }) { Text(stringResource(R.string.action_do)) }
                    TextButton(onClick = ::notAnAction) { Text(stringResource(R.string.action_not)) }
                }
            }
            // Earlier in this conversation, newest first and dimmed: the current answer stays on top, next to the field.
            turns.asReversed().take(MAX_CONTEXT_TURNS).forEach { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(turn.question, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(turn.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** How many earlier exchanges go back to the model: enough for "la primera", short enough to stay fast. */
private const val MAX_CONTEXT_TURNS = 3

/** Android's built-in text to speech in the app's language, shut down when the sheet closes. */
@Composable
private fun rememberSpeaker(locale: Locale): TextToSpeech {
    val context = LocalContext.current
    val speaker = remember {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = locale }
        tts
    }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}


/** What Jarvis asks before acting, and what it says once done. */
private fun actionTexts(action: JarvisAction, res: Resources): Pair<String, String> = when (action) {
    is JarvisAction.AddShopping -> {
        val item = action.item + (action.quantity?.let { " ($it)" } ?: "")
        res.getString(R.string.action_add_shopping, item, action.list.name) to res.getString(R.string.action_added_shopping, item, action.list.name)
    }
    is JarvisAction.CompleteTask ->
        res.getString(R.string.action_complete_task, action.task.title) to res.getString(R.string.action_completed_task, action.task.title)
    is JarvisAction.RecordMovement -> {
        val what = res.getString(
            if (action.category.kind == MoneyKind.INCOME) R.string.action_income else R.string.action_expense,
            formatMoney(action.amountCents),
            action.category.name,
        ) + if (action.date == LocalDate.now()) "" else res.getString(
            R.string.action_on_date,
            action.date.format(DateTimeFormatter.ofPattern(res.getString(R.string.date_long), res.configuration.locales[0])),
        )
        res.getString(R.string.action_record, what) to res.getString(R.string.action_recorded, what)
    }
    is JarvisAction.EditTask -> {
        val locale = res.configuration.locales[0]
        val changes = listOfNotNull(
            action.newTitle?.let { res.getString(R.string.edit_title_to, it) },
            action.due?.let { res.getString(R.string.edit_due_to, it.format(DateTimeFormatter.ofPattern(res.getString(R.string.date_long), locale))) },
            action.remindAt?.let { res.getString(R.string.edit_reminder_to, it.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))) },
            action.priority?.let { res.getString(R.string.edit_priority_to, res.getString(priorityLabel(it)).lowercase(locale)) },
            action.repeat?.let { res.getString(R.string.edit_repeat_to, it.describe(res, locale).replaceFirstChar { c -> c.lowercase(locale) }) },
        ).joinToString(", ")
        res.getString(R.string.action_edit_task, action.task.title, changes) to res.getString(R.string.action_edited_task, action.task.title)
    }
    is JarvisAction.CreateTask -> {
        val locale = res.configuration.locales[0]
        action.repeat?.let { rule ->
            return res.getString(R.string.action_create_task_repeat, action.title, rule.describe(res, locale).replaceFirstChar { c -> c.lowercase(locale) }) to res.getString(R.string.action_created_task, action.title)
        }
        action.remindAt?.let { at ->
            val day = at.toLocalDate().format(DateTimeFormatter.ofPattern(res.getString(R.string.date_long), locale))
            val time = at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            return res.getString(R.string.action_create_task_reminder, action.title, day, time) to res.getString(R.string.action_created_task, action.title)
        }
        val ask = action.due?.let { res.getString(R.string.action_create_task_due, action.title, it.format(DateTimeFormatter.ofPattern(res.getString(R.string.date_long), res.configuration.locales[0]))) }
            ?: res.getString(R.string.action_create_task, action.title)
        ask to res.getString(R.string.action_created_task, action.title)
    }
}

private fun priorityLabel(p: Priority) = when (p) {
    Priority.NONE -> R.string.priority_none
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}

/** Same wording as the repeat dialog ("Cada semana: lun, jue"), outside Compose. */
private fun Recurrence.describe(res: Resources, locale: Locale): String = when (frequency) {
    Frequency.DAILY -> res.getQuantityString(R.plurals.repeat_daily, interval, interval)
    Frequency.WEEKLY -> res.getQuantityString(
        R.plurals.repeat_weekly, interval, interval, weekdays.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale).trimEnd('.') },
    )
    Frequency.MONTHLY -> res.getQuantityString(R.plurals.repeat_monthly, interval, interval, monthDay!!)
}
