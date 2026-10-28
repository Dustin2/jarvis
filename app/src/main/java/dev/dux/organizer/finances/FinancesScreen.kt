package dev.dux.organizer.finances

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.data.Category
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.DebtWithPaid
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.ui.AddButton
import dev.dux.organizer.ui.CappedLabel
import dev.dux.organizer.ui.SPANISH
import dev.dux.organizer.ui.rememberDrafts
import dev.dux.organizer.ui.asHeading
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class FinanceTab(@StringRes val label: Int) {
    Summary(R.string.finance_summary),
    Movements(R.string.finance_movements),
    Debts(R.string.finance_debts),
}

private const val NEW = "new"

@Composable
fun FinancesScreen(vm: FinancesViewModel, onOpenDebt: (String) -> Unit, onBack: () -> Unit, initialMovement: String? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Opened from the search: straight to that movement.
    var tab by rememberSaveable { mutableStateOf(if (initialMovement != null) FinanceTab.Movements else FinanceTab.Summary) }
    // Ids of what is being edited; NEW = creating one.
    var movement by rememberSaveable { mutableStateOf(initialMovement) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingDebt by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.movement_saved)
    val drafts = rememberDrafts()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.finances)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val debts = tab == FinanceTab.Debts
            AddButton(stringResource(if (debts) R.string.new_debt else R.string.register)) {
                if (debts) creatingDebt = true else movement = NEW
            }
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                FinanceTab.entries.forEach {
                    Tab(selected = it == tab, onClick = { tab = it }, text = { CappedLabel(stringResource(it.label)) })
                }
            }
            when (tab) {
                FinanceTab.Summary -> SummaryTab(s, vm, onEditCategory = { category = it })
                FinanceTab.Movements -> MovementsTab(s, vm, onEdit = { movement = it })
                FinanceTab.Debts -> DebtsTab(s.debts, onOpenDebt)
            }
        }

        val editing = s.movements.firstOrNull { it.id == movement }
        if (movement == NEW || editing != null) {
            MovementSheet(
                categories = s.categories,
                initial = editing,
                drafts = drafts,
                onDismiss = { movement = null },
                onSave = { cents, categoryId, date, note, done ->
                    vm.saveMovement(editing?.id, cents, categoryId, date, note) { ok ->
                        done(ok)
                        if (ok) {
                            movement = null
                            drafts.clear("movement.")
                            scope.launch { snackbar.showSnackbar(savedMessage) }
                        }
                    }
                },
                onDelete = editing?.let { { vm.deleteMovement(it.id); movement = null } },
            )
        }
        val editingCategory = s.categories.firstOrNull { it.id == category }
        if (category == NEW || editingCategory != null) {
            CategoryDialog(
                initial = editingCategory,
                onDismiss = { category = null },
                onSave = { name, kind, budget, archived, done ->
                    vm.saveCategory(editingCategory?.id, name, kind, budget, archived) { ok -> done(ok); if (ok) category = null }
                },
            )
        }
        if (creatingDebt) {
            DebtSheet(
                initial = null,
                drafts = drafts,
                onDismiss = { creatingDebt = false },
                onSave = { person, direction, cents, due, note, done ->
                    vm.addDebt(person, direction, cents, due, note) { ok ->
                        done(ok)
                        if (ok) {
                            creatingDebt = false
                            drafts.clear("debt.")
                        }
                    }
                },
            )
        }
    }
}

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", SPANISH)

@Composable
private fun MonthBar(month: YearMonth, vm: FinancesViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previousMonth) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_month)) }
        Text(
            month.format(monthFormat).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).asHeading(),
        )
        IconButton(onClick = vm::nextMonth) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.next_month)) }
    }
}

@Composable
private fun AmountRow(@StringRes label: Int, cents: Long, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.weight(1f))
        Text(formatMoney(cents), color = color, style = MaterialTheme.typography.titleMedium)
    }
}

/** Spent against the budget: the bar fills up to the budget; going over is said in words, not only in red. */
@Composable
fun BudgetProgress(spentCents: Long, budgetCents: Long) {
    val over = spentCents - budgetCents
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (over > 0) stringResource(R.string.over_budget, formatMoney(over)) else stringResource(R.string.spent_of_budget, formatMoney(spentCents), formatMoney(budgetCents)),
            color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (spentCents.toFloat() / budgetCents).coerceIn(0f, 1f) },
            color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyListScope.heading(@StringRes title: Int, action: (@Composable () -> Unit)? = null) {
    item(key = "heading-$title") {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).asHeading())
            action?.invoke()
        }
    }
}

@Composable
private fun SummaryTab(s: FinancesState, vm: FinancesViewModel, onEditCategory: (String) -> Unit) {
    val summary = s.summary
    val incomeByCategory = s.movements.filter { it.kind == MoneyKind.INCOME }.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    val incomeCategories = s.categories.filter { it.kind == MoneyKind.INCOME && (!it.archived || it.id in incomeByCategory) }
    val shown = summary.lines.map { it.category.id }.toSet() + incomeCategories.map { it.id }
    val archived = s.categories.filter { it.archived && it.id !in shown }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { MonthBar(s.month, vm) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountRow(R.string.income_total, summary.incomeCents)
                    AmountRow(R.string.expense_total, summary.expenseCents)
                    AmountRow(R.string.balance, summary.balanceCents, if (summary.balanceCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (summary.balanceCents < 0) Text(stringResource(R.string.negative_balance), color = MaterialTheme.colorScheme.error)
                    summary.budgetCents?.let { BudgetProgress(summary.expenseCents, it) }
                }
            }
        }
        heading(R.string.expenses_by_category) {
            TextButton(onClick = { onEditCategory(NEW) }) { Text(stringResource(R.string.new_category)) }
        }
        items(summary.lines, key = { it.category.id }) { line ->
            ListItem(
                onClick = { onEditCategory(line.category.id) },
                supportingContent = line.category.monthlyBudgetCents?.let { budget -> { BudgetProgress(line.spentCents, budget) } },
                trailingContent = { Text(formatMoney(line.spentCents)) },
            ) { Text(categoryName(line.category)) }
        }
        heading(R.string.income_by_category)
        items(incomeCategories, key = { it.id }) { c ->
            ListItem(onClick = { onEditCategory(c.id) }, trailingContent = { Text(formatMoney(incomeByCategory[c.id] ?: 0)) }) { Text(categoryName(c)) }
        }
        if (archived.isNotEmpty()) {
            heading(R.string.archived_categories)
            items(archived, key = { it.id }) { c -> ListItem(onClick = { onEditCategory(c.id) }) { Text(c.name) } }
        }
    }
}

@Composable
private fun categoryName(c: Category) = if (c.archived) stringResource(R.string.archived_name, c.name) else c.name

@Composable
private fun MovementsTab(s: FinancesState, vm: FinancesViewModel, onEdit: (String) -> Unit) {
    val today by rememberToday()
    val names = s.categories.associate { it.id to it.name }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { MonthBar(s.month, vm) }
        if (s.movements.isEmpty()) {
            item {
                Text(stringResource(R.string.empty_movements), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        }
        items(s.movements, key = { it.id }) { m ->
            val income = m.kind == MoneyKind.INCOME
            ListItem(
                onClick = { onEdit(m.id) },
                overlineContent = { Text(m.date.label(today)) },
                supportingContent = m.note?.let { { Text(it, maxLines = 1) } },
                trailingContent = {
                    // The sign says it too, not only the color.
                    Text(
                        (if (income) "+" else "-") + formatMoney(m.amountCents),
                        color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            ) { Text(names[m.categoryId].orEmpty()) }
        }
    }
}

@Composable
private fun DebtsTab(debts: List<DebtWithPaid>, onOpen: (String) -> Unit) {
    val open = debts.filter { it.debt.settledAt == null }
    val iOwe = open.filter { it.debt.direction == DebtDirection.I_OWE }
    val owedToMe = open.filter { it.debt.direction == DebtDirection.OWED_TO_ME }
    val settled = debts.filter { it.debt.settledAt != null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountRow(R.string.debt_i_owe, iOwe.sumOf { it.remainingCents })
                    AmountRow(R.string.debt_owed_to_me, owedToMe.sumOf { it.remainingCents })
                }
            }
        }
        if (debts.isEmpty()) {
            item { Text(stringResource(R.string.empty_debts), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
        }
        debtSection(R.string.debt_i_owe, iOwe, onOpen)
        debtSection(R.string.debt_owed_to_me, owedToMe, onOpen)
        debtSection(R.string.debts_settled, settled, onOpen)
    }
}

private fun LazyListScope.debtSection(@StringRes title: Int, debts: List<DebtWithPaid>, onOpen: (String) -> Unit) {
    if (debts.isEmpty()) return
    heading(title)
    items(debts, key = { it.debt.id }) { DebtRow(it, onClick = { onOpen(it.debt.id) }) }
}

@Composable
private fun DebtRow(item: DebtWithPaid, onClick: () -> Unit) {
    val today by rememberToday()
    val debt = item.debt
    val overdue = debt.settledAt == null && debt.dueDate?.let { it < today } == true
    val parts = buildList {
        add(
            if (debt.settledAt != null) stringResource(R.string.debt_settled)
            else stringResource(R.string.remaining_of, formatMoney(item.remainingCents), formatMoney(debt.amountCents))
        )
        debt.dueDate?.let { add(stringResource(R.string.due_label, it.label(today))) }
    }
    ListItem(
        onClick = onClick,
        supportingContent = {
            Text(parts.joinToString(" · "), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = { Text(formatMoney(if (debt.settledAt != null) debt.amountCents else item.remainingCents)) },
    ) { Text(debt.person) }
}
