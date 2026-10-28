package dev.dux.organizer.ask

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
fun AskSheet(ask: AskJarvis, facts: suspend () -> String, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (ask.installed) focus.requestFocus() }
    val thinking = stringResource(R.string.ask_thinking)
    val failed = stringResource(R.string.ask_failed)
    fun send() {
        if (question.isBlank() || job?.isActive == true) return
        answer = thinking
        job = scope.launch {
            var first = true
            runCatching {
                ask.ask(question.trim(), facts()).collect { answer = if (first) it else answer + it; first = false }
            }.onFailure { answer = failed }
        }
    }
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
                trailingIcon = { IconButton(::send, enabled = question.isNotBlank()) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.ask_send)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            if (answer.isNotEmpty()) Text(answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
