package dev.dux.organizer.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.R
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.ShoppingRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.tasks.label
import dev.dux.organizer.ui.asHeading
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/** Where a result leads. */
sealed interface SearchTarget {
    data class Task(val id: String) : SearchTarget
    data class Project(val id: String) : SearchTarget
    data class ShoppingList(val id: String) : SearchTarget
    data class Movement(val id: String, val month: YearMonth) : SearchTarget
    data class Debt(val id: String) : SearchTarget
}

@OptIn(FlowPreview::class)
class SearchViewModel(tasks: TaskRepository, projects: ProjectRepository, shopping: ShoppingRepository, finance: FinanceRepository) : ViewModel() {
    val query = MutableStateFlow("")

    private val data = combine(
        combine(tasks.all, projects.visible, shopping.lists, shopping.allItems) { t, p, l, i -> SearchData(tasks = t, projects = p, lists = l, items = i) },
        combine(finance.categories, finance.allMovements, finance.debts) { c, m, d -> Triple(c, m, d) },
    ) { base, (categories, movements, debts) -> base.copy(categories = categories, movements = movements, debts = debts.map { it.debt }) }

    // A short pause while typing avoids recomputing on every key.
    val results = combine(query.debounce(150), data) { q, d -> search(q, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}

private const val PREVIEW = 5

@Composable
fun SearchScreen(vm: SearchViewModel, onOpen: (SearchTarget) -> Unit, onBack: () -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val today by rememberToday()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = if (query.isNotEmpty()) ({
                            IconButton(onClick = { vm.query.value = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search)) }
                        }) else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isNotBlank() && results.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.no_results, query.trim()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            group(R.string.tab_tasks, results.tasks, key = { "t-${it.id}" }) { task ->
                val finished = task.status !in setOf(TaskStatus.INBOX, TaskStatus.TODO)
                val inNotes = task.notes?.takeIf { matchRange(task.title, query) == null }
                Row(
                    headline = task.title,
                    supporting = inNotes ?: if (finished) stringResource(task.status.label) else null,
                    query = query,
                    onClick = { onOpen(SearchTarget.Task(task.id)) },
                )
            }
            group(R.string.tab_projects, results.projects, key = { "p-${it.id}" }) { project ->
                Row(project.name, null, query) { onOpen(SearchTarget.Project(project.id)) }
            }
            group(R.string.tab_shopping, results.items, key = { "i-${it.item.id}" }) { (item, listName) ->
                val details = listOfNotNull(item.quantity, listName, if (item.checked) stringResource(R.string.in_cart_short) else null)
                Row(item.name, details.joinToString(" · "), query) { onOpen(SearchTarget.ShoppingList(item.listId)) }
            }
            group(R.string.finance_movements, results.movements, key = { "m-${it.movement.id}" }) { (movement, categoryName) ->
                val sign = if (movement.kind == MoneyKind.INCOME) "+" else "-"
                Row(
                    headline = movement.note ?: categoryName,
                    supporting = listOf(sign + formatMoney(movement.amountCents), categoryName, movement.date.label(today)).joinToString(" · "),
                    query = query,
                    onClick = { onOpen(SearchTarget.Movement(movement.id, YearMonth.from(movement.date))) },
                )
            }
            group(R.string.finance_debts, results.debts, key = { "d-${it.id}" }) { debt ->
                val direction = stringResource(if (debt.direction == DebtDirection.I_OWE) R.string.debt_i_owe else R.string.debt_owed_to_me)
                val state = if (debt.settledAt != null) stringResource(R.string.debt_settled) else formatMoney(debt.amountCents)
                Row(debt.person, listOfNotNull(direction, state, debt.note).joinToString(" · "), query) { onOpen(SearchTarget.Debt(debt.id)) }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A section with up to [PREVIEW] results and "Ver todos (n)" to expand it. */
private fun <T> LazyListScope.group(@StringRes title: Int, hits: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    if (hits.isEmpty()) return
    item(key = "heading-$title") {
        Text(
            "${stringResource(title)} (${hits.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).asHeading(),
        )
    }
    item(key = "group-$title") {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Column {
            (if (expanded) hits else hits.take(PREVIEW)).forEach { key(key(it)) { row(it) } }
            if (!expanded && hits.size > PREVIEW) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.see_all, hits.size))
                }
            }
        }
    }
}

@Composable
private fun Row(headline: String, supporting: String?, query: String, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        supportingContent = supporting?.let { { Text(highlight(it, query), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
    ) { Text(highlight(headline, query), maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

/** Bolds the part that matched, so it is clear why the result appeared. */
private fun highlight(text: String, query: String): AnnotatedString {
    val range = matchRange(text, query) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(range)) }
        append(text.substring(range.last + 1))
    }
}
