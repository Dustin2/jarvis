package dev.dux.organizer.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.R
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.tasks.TaskRow
import dev.dux.organizer.ui.SPANISH
import dev.dux.organizer.ui.rememberToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TodayViewModel(private val tasks: TaskRepository, projects: ProjectRepository) : ViewModel() {
    /** Tareas abiertas + nombre de proyecto por id. null = cargando. */
    val state = combine(tasks.open, projects.visible) { open, list -> open to list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete(id: String) {
        viewModelScope.launch { tasks.setStatus(id, TaskStatus.DONE) }
    }
}

@Composable
fun TodayScreen(vm: TodayViewModel, onOpen: (String) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today by rememberToday()
    val (open, projectNames) = state ?: return
    val plan = remember(open, today) { planDay(open, today) }
    val row: @Composable (Task) -> Unit = { task ->
        TaskRow(task, onOpen = { onOpen(task.id) }, onToggleDone = { vm.complete(task.id) }, projectName = projectNames[task.projectId])
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tab_today), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                }
                Text(
                    today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(SPANISH)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pluralStringResource(R.plurals.open_count, plan.openCount, plan.openCount) + " · " +
                        pluralStringResource(R.plurals.overdue_count, plan.overdue.size, plan.overdue.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { NextStepCard(plan.nextStep, onOpen) }
        section(R.string.overdue, plan.overdue, row)
        section(R.string.today, plan.today, row)
        section(R.string.upcoming, plan.upcoming, row)
    }
}

@Composable
private fun NextStepCard(task: Task?, onOpen: (String) -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.next_step), style = MaterialTheme.typography.labelLarge)
            Text(task?.title ?: stringResource(R.string.empty_today), style = MaterialTheme.typography.headlineSmall)
        }
    }
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    if (task == null) Card(modifier, colors = colors) { content() }
    else Card(onClick = { onOpen(task.id) }, modifier = modifier, colors = colors) { content() }
}

private fun LazyListScope.section(@StringRes title: Int, tasks: List<Task>, row: @Composable (Task) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(tasks, key = { "$title-${it.id}" }) { row(it) }
}
