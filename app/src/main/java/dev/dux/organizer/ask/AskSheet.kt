package dev.dux.organizer.ask

import java.util.Locale
import dev.dux.organizer.ui.appLocale
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.data.MoneyKind
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSheet(
    ask: AskJarvis,
    data: suspend () -> AskData,
    onAction: suspend (JarvisAction) -> (suspend () -> Unit)?,
    startListening: Boolean = false,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
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
        ask.ask(q, facts).collect { answer = if (first) it else answer + it; first = false }
        if (spoken) speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "answer")
    }
    fun launch(block: suspend () -> Unit) {
        job = scope.launch { runCatching { block() }.onFailure { answer = failed } }
    }
    fun send(byVoice: Boolean = false) {
        if (question.isBlank() || job?.isActive == true) return
        speaker.stop()
        proposal = null
        undo = null
        spoken = byVoice
        answer = thinking
        launch {
            val q = question.trim()
            val d = data()
            // A question mark means a question: no need to look for an action.
            val action = if (q.endsWith("?")) null else parseAction(ask.proposeAction(q, d.facts()), q, d.open, d.lists, d.today, d.categories)
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
        launch { answerQuestion(question.trim(), data().facts()) }
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
    // From the widget or the app shortcut: straight to dictation; otherwise ready to type.
    LaunchedEffect(Unit) { if (ask.installed) { if (startListening) dictate() else focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        }
    }
}

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
        )
        res.getString(R.string.action_record, what) to res.getString(R.string.action_recorded, what)
    }
    is JarvisAction.CreateTask -> {
        val ask = action.due?.let { res.getString(R.string.action_create_task_due, action.title, it.format(DateTimeFormatter.ofPattern(res.getString(R.string.date_long), res.configuration.locales[0]))) }
            ?: res.getString(R.string.action_create_task, action.title)
        ask to res.getString(R.string.action_created_task, action.title)
    }
}
