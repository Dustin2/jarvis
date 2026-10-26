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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import dev.dux.organizer.data.Project
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
        DateField(R.string.due_date, task.dueDate, vm::setDueDate)
        DateField(R.string.scheduled_date, task.scheduledDate, vm::setScheduledDate)
        val projects by vm.projects.collectAsStateWithLifecycle()
        ProjectField(projects.orEmpty(), task.projectId, vm::setProject)
    }
}

@Composable
private fun Field(title: Int, value: String, onClick: () -> Unit, menu: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = onClick) { Text(value) }
            menu()
        }
    }
}

// DatePicker sigue marcado experimental, pero es el selector oficial de M3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(title: Int, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today by rememberToday()
    Field(title, date?.label(today) ?: stringResource(R.string.no_date), onClick = { picking = true })
    if (!picking) return

    // El DatePicker trabaja en milisegundos UTC a medianoche; convertir siempre en UTC evita el error de "un día antes".
    val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(onClick = {
                picking = false
                state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.accept)) }
        },
        dismissButton = {
            TextButton(onClick = { picking = false; onChange(null) }, enabled = date != null) {
                Text(stringResource(R.string.remove_date))
            }
        },
    ) { DatePicker(state) }
}

@Composable
private fun ProjectField(projects: List<Project>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = when (selectedId) {
        null -> stringResource(R.string.no_project)
        // Un proyecto archivado no aparece en la lista, pero la tarea sigue ligada a él.
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
