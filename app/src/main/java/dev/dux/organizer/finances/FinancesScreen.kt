package dev.dux.organizer.finances

import dev.dux.organizer.ui.rememberDateFormat
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import dev.dux.organizer.ui.ChoiceChips
import dev.dux.organizer.ui.blockRowShapes
import dev.dux.organizer.ui.inBlock
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import dev.dux.organizer.R
import dev.dux.organizer.data.Category
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.DebtWithPaid
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.ui.RoundAddButton
import dev.dux.organizer.ui.theme.LocalWarningColor
import dev.dux.organizer.ui.ScreenHeader
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ScaffoldDefaults
import dev.dux.organizer.ui.rememberDrafts
import dev.dux.organizer.ui.asHeading
import dev.dux.organizer.ui.inlineLabel
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
/**
 * A main tab when [onBack] is null (its own header, no top bar); a pushed screen with a back arrow when
 * opened from the search on a given movement.
 */
fun FinancesScreen(
    vm: FinancesViewModel,
    onOpenDebt: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialMovement: String? = null,
) {
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

    val newCategory: @Composable ColumnScope.(() -> Unit) -> Unit = { close ->
        DropdownMenuItem(text = { Text(stringResource(R.string.new_category)) }, onClick = { close(); category = NEW })
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.finances)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                    },
                )
            }
        },
        // As a tab, the main screen already handled the system bars.
        contentWindowInsets = if (onBack == null) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val debts = tab == FinanceTab.Debts
            // The same round "+" as the other tabs; the description says what it adds here.
            RoundAddButton(stringResource(if (debts) R.string.new_debt else R.string.register)) {
                if (debts) creatingDebt = true else movement = NEW
            }
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(Modifier.padding(padding)) {
            if (onBack == null) ScreenHeader(R.string.finances, onSearch, menu = newCategory)
            // Chips, not tabs: at large font sizes they wrap instead of cutting "Movimientos".
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                ChoiceChips(FinanceTab.entries, tab, { stringResource(it.label) }) { tab = it }
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


@Composable
private fun MonthBar(month: YearMonth, vm: FinancesViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previousMonth) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_month)) }
        Text(
            month.format(rememberDateFormat(R.string.month_year)).replaceFirstChar { it.uppercase() },
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

/**
 * Spent against the budget: the bar fills up to the budget; going over is said in words, not only in red.
 * [total]: the month's whole budget, labelled ("Gastado: …") since it has no category name above it.
 */
@Composable
fun BudgetProgress(spentCents: Long, budgetCents: Long, total: Boolean = false) {
    val over = spentCents - budgetCents
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (total) R.string.spent_of_budget_total else R.string.spent_of_budget, formatMoney(spentCents), formatMoney(budgetCents)) +
                if (over > 0) " · " + stringResource(R.string.over_budget, formatMoney(over)) else "",
            color = if (over > 0) LocalWarningColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (spentCents.toFloat() / budgetCents).coerceIn(0f, 1f) },
            color = if (over > 0) LocalWarningColor.current else MaterialTheme.colorScheme.primary,
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
        item { BalanceHero(s.month, summary, vm) }
        summary.budgetCents?.let { budget ->
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Box(Modifier.padding(16.dp)) { BudgetProgress(summary.expenseCents, budget, total = true) }
                }
            }
        }
        heading(R.string.expenses_by_category)
        itemsIndexed(summary.lines, key = { _, line -> line.category.id }) { index, line ->
            CategoryRow(line.category, line.spentCents, Modifier.inBlock(index, summary.lines.lastIndex)) { onEditCategory(line.category.id) }
        }
        heading(R.string.income_by_category)
        itemsIndexed(incomeCategories, key = { _, c -> c.id }) { index, c ->
            CategoryRow(c, incomeByCategory[c.id] ?: 0, Modifier.inBlock(index, incomeCategories.lastIndex)) { onEditCategory(c.id) }
        }
        if (archived.isNotEmpty()) {
            heading(R.string.archived_categories)
            itemsIndexed(archived, key = { _, c -> c.id }) { index, c ->
                CategoryRow(c, null, Modifier.inBlock(index, archived.lastIndex)) { onEditCategory(c.id) }
            }
        }
    }
}

/** "Te queda" first: the month's balance, with what came in and went out, and the month arrows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceHero(month: YearMonth, summary: MonthSummary, vm: FinancesViewModel) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previousMonth) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_month)) }
                Text(month.format(rememberDateFormat(R.string.month_year)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).asHeading())
                IconButton(onClick = vm::nextMonth) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.next_month)) }
            }
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(summary.balanceCents), style = MaterialTheme.typography.displaySmall)
                if (summary.balanceCents < 0) Text(stringResource(R.string.negative_balance), style = MaterialTheme.typography.bodyMedium)
                // Two tiles side by side; one per line at large font sizes, where a split amount wraps mid-number.
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.5f) 1 else 2,
                ) {
                    Tile(stringResource(R.string.income_in), "+" + formatMoney(summary.incomeCents), onContainer, Modifier.weight(1f))
                    Tile(stringResource(R.string.expense_out), "-" + formatMoney(summary.expenseCents), onContainer, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, amount: String, onContainer: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = onContainer.copy(alpha = 0.10f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(amount, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A category with its icon. Expense categories with a budget show "gastado / presupuesto" and a bar
 * (over budget in orange, with text); others show only the amount. [amountCents] null: no amount (archived).
 */
@Composable
private fun CategoryRow(category: Category, amountCents: Long?, modifier: Modifier, onClick: () -> Unit) {
    val budget = category.monthlyBudgetCents
    ListItem(
        onClick = onClick,
        modifier = modifier,
        shapes = blockRowShapes(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(categoryIcon(category)), contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
        supportingContent = if (budget != null && amountCents != null) ({ BudgetProgress(amountCents, budget) }) else null,
        trailingContent = if (budget == null && amountCents != null) ({ Text(formatMoney(amountCents)) }) else null,
    ) { Text(categoryName(category)) }
}

// shortcut: icons follow the names of the categories Jarvis creates; a renamed or new category gets the
// generic tag. Store an icon per category if people want to choose them.
@DrawableRes
private fun categoryIcon(category: Category): Int = when {
    category.kind == MoneyKind.INCOME -> R.drawable.ic_cat_income
    else -> when (category.name.lowercase()) {
        // Spanish and English starting names (see seed_expense_categories).
        "comida", "food" -> R.drawable.ic_cat_food
        "transporte", "transport" -> R.drawable.ic_cat_car
        "casa", "home" -> R.drawable.ic_cat_home
        "servicios", "utilities" -> R.drawable.ic_cat_bolt
        "salud", "health" -> R.drawable.ic_cat_health
        "entretenimiento", "entertainment" -> R.drawable.ic_cat_fun
        else -> R.drawable.ic_cat_other
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
        // Grouped by day: the date is said once (with the day's balance when it has several), instead of on every row.
        s.movements.groupBy { it.date }.forEach { (date, day) ->
            item(key = "day-$date") {
                val net = day.sumOf { if (it.kind == MoneyKind.INCOME) it.amountCents else -it.amountCents }
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(date.label(today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).asHeading())
                    // With a single movement the total would repeat its amount.
                    if (day.size > 1) {
                        Text((if (net > 0) "+" else "") + formatMoney(net), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(day, key = { it.id }) { m -> MovementRow(m, names[m.categoryId].orEmpty(), onClick = { onEdit(m.id) }) }
        }
    }
}

@Composable
private fun MovementRow(m: Movement, categoryName: String, onClick: () -> Unit) {
    val income = m.kind == MoneyKind.INCOME
    ListItem(
        onClick = onClick,
        supportingContent = m.note?.let { { Text(it, maxLines = 1) } },
        trailingContent = {
            // The sign says it too, not only the color.
            Text(
                (if (income) "+" else "-") + formatMoney(m.amountCents),
                color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        },
    ) { Text(categoryName) }
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
        // Overdue is said in words too, not only in red.
        debt.dueDate?.let { add(stringResource(if (overdue) R.string.debt_overdue else R.string.due_label, it.inlineLabel(today))) }
    }
    ListItem(
        onClick = onClick,
        supportingContent = {
            Text(parts.joinToString(" · "), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) { Text(debt.person) }
}
