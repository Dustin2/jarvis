package dev.dux.organizer.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.dux.organizer.app
import dev.dux.organizer.ask.AskSheet
import dev.dux.organizer.ask.askFacts
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import dev.dux.organizer.ui.blockRowShapes
import dev.dux.organizer.ui.inBlock
import dev.dux.organizer.ui.inlineLabel
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import dev.dux.organizer.ui.ScreenHeader
import dev.dux.organizer.ui.rememberToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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

    // shortcut: "today" is read when the list changes; just after midnight it can count yesterday's until something changes.
    /** Tasks completed today, for the "3 de 8 hechas" progress. */
    val doneToday = tasks.finished.map { list ->
        val today = LocalDate.now()
        list.count { it.status == TaskStatus.DONE && it.completedAt?.atZone(ZoneId.systemDefault())?.toLocalDate() == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Completes the task and hands back an undo for the "Deshacer" message. */
    fun complete(task: Task, onCompleted: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            tasks.complete(task.id)?.let { undo -> onCompleted { viewModelScope.launch { undo() } } }
        }
    }
}

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFinances: () -> Unit,
    onSearch: () -> Unit,
    offerUndo: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val state by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val backupOverdue by vm.backupOverdue.collectAsStateWithLifecycle()
    val doneToday by vm.doneToday.collectAsStateWithLifecycle()
    val today by rememberToday()
    val (open, projectNames) = state ?: return
    val plan = remember(open, today) { planDay(open, today) }
    var asking by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        val app = LocalContext.current.app
        // Shopping is read only when asking: Mi día does not show it, so it is not observed here.
        AskSheet(app.askJarvis, { askFacts(open, projectNames, month, today, app.shopping.lists.first(), app.shopping.allItems.first()) }, onOpenSettings, onDismiss = { asking = false })
    }
    val row: @Composable (Task, Modifier) -> Unit = { task, blockModifier ->
        TaskRow(
            task,
            onOpen = { onOpen(task.id) },
            onToggleDone = { vm.complete(task) { undo -> offerUndo(resources.getString(R.string.task_completed, task.title), undo) } },
            projectName = projectNames[task.projectId],
            modifier = blockModifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shapes = blockRowShapes(),
        )
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            ScreenHeader(
                R.string.tab_today,
                onSearch,
                overline = today.format(headerDate).replaceFirstChar { it.uppercase() },
                extra = {
                    IconButton(onClick = { asking = true }) { Icon(painterResource(R.drawable.ic_ask), stringResource(R.string.ask_jarvis)) }
                    IconButton(onClick = onOpenSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings)) }
                },
            )
        }
        if (backupOverdue) item { BackupWarningCard(onOpenSettings) }
        // Today's work: what was done today plus what is still due or planned for today.
        val total = doneToday + plan.overdue.size + plan.today.size
        if (total > 0) item { ProgressCard(doneToday, total, plan.overdue.size) }
        val next = plan.nextStep
        item {
            NextStepCard(next, onOpen, onComplete = { task ->
                vm.complete(task) { undo -> offerUndo(resources.getString(R.string.task_completed, task.title), undo) }
            })
        }
        // The next step already has its own card: listing it again below repeated it.
        section(R.string.overdue, plan.overdue - setOfNotNull(next), row)
        section(R.string.today, plan.today - setOfNotNull(next), row)
        section(R.string.upcoming, plan.upcoming, row)
        month?.let { item { MonthRow(it, onOpenFinances) } }
    }
}

private val headerDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)

/** "3 de 8 hechas" with a ring, and the overdue count in words. */
@Composable
private fun ProgressCard(done: Int, total: Int, overdue: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.size(56.dp),
                strokeWidth = 7.dp,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Column {
                Text(pluralStringResource(R.plurals.progress_done, total, done, total), style = MaterialTheme.typography.titleLarge)
                if (overdue > 0) {
                    Text(pluralStringResource(R.plurals.overdue_count, overdue, overdue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextStepCard(task: Task?, onOpen: (String) -> Unit, onComplete: (Task) -> Unit) {
    val today by rememberToday()
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.next_step), style = MaterialTheme.typography.labelLarge)
            Text(task?.title ?: stringResource(R.string.empty_today), style = MaterialTheme.typography.headlineSmall)
            if (task != null) {
                // Wraps: at large font sizes a Row squeezed "Completar" into one letter per line.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    whenLabel(task, today)?.let {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)) {
                            Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Button(onClick = { onComplete(task) }) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                        Text(stringResource(R.string.complete), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    if (task == null) Card(modifier, colors = colors) { content() }
    else Card(onClick = { onOpen(task.id) }, modifier = modifier, colors = colors) { content() }
}

/** When the next step is due, in words: "Vencida ayer", "Vence hoy", "Desde ayer", "Para hoy". */
@Composable
private fun whenLabel(task: Task, today: LocalDate): String? {
    val due = task.dueDate
    val scheduled = task.scheduledDate
    return when {
        due != null && due < today -> stringResource(R.string.overdue_label, due.inlineLabel(today))
        due != null -> stringResource(R.string.due_label, due.inlineLabel(today))
        scheduled != null -> stringResource(if (scheduled < today) R.string.scheduled_since else R.string.scheduled_label, scheduled.inlineLabel(today))
        else -> null
    }
}

@Composable
private fun BackupWarningCard(onClick: () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = colors) {
        Text(stringResource(R.string.backup_overdue), Modifier.padding(20.dp))
    }
}

/** The month's spending in one row; opens Finanzas. */
@Composable
private fun MonthRow(summary: MonthSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_finances), contentDescription = null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                val budget = summary.budgetCents
                if (budget == null) Text(stringResource(R.string.month_spent, formatMoney(summary.expenseCents)))
                else BudgetProgress(summary.expenseCents, budget, total = true)
            }
        }
    }
}

/** A titled group of tasks drawn as one rounded block, like the task detail's options. */
private fun LazyListScope.section(@StringRes title: Int, tasks: List<Task>, row: @Composable (Task, Modifier) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp).asHeading(),
        )
    }
    itemsIndexed(tasks, key = { _, task -> "$title-${task.id}" }) { index, task -> row(task, Modifier.inBlock(index, tasks.lastIndex)) }
}
