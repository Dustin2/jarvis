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
import dev.dux.organizer.backup.AutoBackup
import dev.dux.organizer.backup.backupOverdue
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.finances.BudgetProgress
import dev.dux.organizer.finances.MonthSummary
import dev.dux.organizer.finances.monthSummary
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.tasks.TaskRow
import dev.dux.organizer.ui.SPANISH
import dev.dux.organizer.ui.rememberToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.dux.organizer.ui.asHeading

class TodayViewModel(private val tasks: TaskRepository, projects: ProjectRepository, finance: FinanceRepository, autoBackup: AutoBackup) : ViewModel() {
    /** Open tasks + project name by id. null = loading. */
    val state = combine(tasks.open, projects.visible) { open, list -> open to list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // shortcut: the month is fixed when the screen opens; if the app stays open past month end it shows the old one until reopened.
    val month = combine(finance.categories, finance.movements(YearMonth.now())) { categories, movements -> monthSummary(categories, movements) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Automatic backup is on but has not saved anything for days: better to know before the data is needed. */
    val backupOverdue = autoBackup.state.map { backupOverdue(it.lastSuccess, it.enabledAt, Instant.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun complete(id: String) {
        viewModelScope.launch { tasks.setStatus(id, TaskStatus.DONE) }
    }
}

@Composable
fun TodayScreen(vm: TodayViewModel, onOpen: (String) -> Unit, onOpenSettings: () -> Unit, onOpenFinances: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val backupOverdue by vm.backupOverdue.collectAsStateWithLifecycle()
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
                    Text(stringResource(R.string.tab_today), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).asHeading())
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
        if (backupOverdue) item { BackupWarningCard(onOpenSettings) }
        item { NextStepCard(plan.nextStep, onOpen) }
        month?.let { item { MonthCard(it, onOpenFinances) } }
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

@Composable
private fun BackupWarningCard(onClick: () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = colors) {
        Text(stringResource(R.string.backup_overdue), Modifier.padding(20.dp))
    }
}

/** The month's spending at a glance; opens Finanzas. */
@Composable
private fun MonthCard(summary: MonthSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.month_spending) + " · " + stringResource(R.string.finances), style = MaterialTheme.typography.labelLarge)
            val budget = summary.budgetCents
            if (budget == null) Text(stringResource(R.string.month_spent, formatMoney(summary.expenseCents)), style = MaterialTheme.typography.titleLarge)
            else BudgetProgress(summary.expenseCents, budget)
        }
    }
}

private fun LazyListScope.section(@StringRes title: Int, tasks: List<Task>, row: @Composable (Task) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).asHeading(),
        )
    }
    items(tasks, key = { "$title-${it.id}" }) { row(it) }
}
