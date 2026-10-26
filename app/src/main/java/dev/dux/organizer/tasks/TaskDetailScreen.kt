package dev.dux.organizer.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus

/** Los cambios se guardan al instante: no hay botón Guardar ni forma de perder una edición. */
@Composable
fun TaskDetailScreen(vm: TaskDetailViewModel, onBack: () -> Unit) {
    val task by vm.task.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
    // Estado local del texto: se inicializa una vez y no se pisa con lo que vuelve de la base.
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
    }
}

@Composable
private fun <T> Choice(title: Int, options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(stringResource(label(option)), maxLines = 1) },
                )
            }
        }
    }
}
