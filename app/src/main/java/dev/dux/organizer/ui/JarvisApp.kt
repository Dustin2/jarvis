package dev.dux.organizer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.dux.organizer.R
import dev.dux.organizer.app
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import dev.dux.organizer.finances.DebtDetailScreen
import dev.dux.organizer.finances.DebtDetailViewModel
import dev.dux.organizer.finances.FinancesScreen
import dev.dux.organizer.finances.FinancesViewModel
import dev.dux.organizer.inbox.CaptureSheet
import dev.dux.organizer.tasks.InboxScreen
import dev.dux.organizer.tasks.TaskDetailScreen
import dev.dux.organizer.tasks.TaskDetailViewModel
import dev.dux.organizer.tasks.TaskListViewModel
import dev.dux.organizer.tasks.TasksScreen
import dev.dux.organizer.projects.ProjectDetailScreen
import dev.dux.organizer.projects.ProjectDetailViewModel
import dev.dux.organizer.projects.ProjectsScreen
import dev.dux.organizer.projects.ProjectsViewModel
import dev.dux.organizer.search.SearchScreen
import dev.dux.organizer.search.SearchTarget
import dev.dux.organizer.search.SearchViewModel
import dev.dux.organizer.settings.SettingsScreen
import dev.dux.organizer.shopping.AddItemSheet
import dev.dux.organizer.shopping.ShoppingScreen
import dev.dux.organizer.shopping.ShoppingViewModel
import dev.dux.organizer.settings.SettingsViewModel
import dev.dux.organizer.today.TodayScreen
import dev.dux.organizer.today.TodayViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.YearMonth

@Serializable data object Main : NavKey
@Serializable data class TaskDetail(val id: String) : NavKey
@Serializable data class ProjectDetail(val id: String) : NavKey
@Serializable data object Settings : NavKey
/** [month] ("2026-10") and [movementId] open a movement found by the search. */
@Serializable data class Finances(val month: String? = null, val movementId: String? = null) : NavKey
@Serializable data object Search : NavKey
@Serializable data class DebtDetail(val id: String) : NavKey

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Today(R.string.tab_today, R.drawable.ic_today),
    Inbox(R.string.tab_inbox, R.drawable.ic_inbox),
    Tasks(R.string.tab_tasks, R.drawable.ic_tasks),
    Projects(R.string.tab_projects, R.drawable.ic_projects),
    Shopping(R.string.tab_shopping, R.drawable.ic_shopping),
}

@Composable
fun JarvisApp(taskToOpen: String? = null, onTaskOpened: () -> Unit = {}, capture: Boolean = false, onCaptureShown: () -> Unit = {}) {
    // The back stack saves itself (rotation, process death); that is why the keys are @Serializable.
    val backStack = rememberNavBackStack(Main)
    LaunchedEffect(taskToOpen) {
        if (taskToOpen != null) {
            backStack.add(TaskDetail(taskToOpen))
            onTaskOpened()
        }
    }
    LaunchedEffect(capture) {
        if (capture) {
            // The capture sheet lives on the main screen: go back to it first.
            while (backStack.size > 1) backStack.removeLastOrNull()
        }
    }
    val app = LocalContext.current.app
    // Hoisted so the search can open Compras on a given list.
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var listToOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val openTask: (String) -> Unit = { backStack.add(TaskDetail(it)) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Each screen has its own saved state and its own ViewModels.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    tasksVm = viewModel { TaskListViewModel(app.tasks, app.projects) },
                    todayVm = viewModel { TodayViewModel(app.tasks, app.projects, app.finance, app.autoBackup) },
                    projectsVm = viewModel { ProjectsViewModel(app.projects, app.tasks) },
                    shoppingVm = viewModel { ShoppingViewModel(app.shopping) },
                    onOpenTask = openTask,
                    onOpenProject = { backStack.add(ProjectDetail(it)) },
                    onOpenSettings = { backStack.add(Settings) },
                    onOpenFinances = { backStack.add(Finances()) },
                    onSearch = { backStack.add(Search) },
                    tab = tab,
                    onTabChange = { tab = it },
                    listToOpen = listToOpen,
                    onListOpened = { listToOpen = null },
                    captureRequested = capture,
                    onCaptureShown = onCaptureShown,
                )
            }
            entry<TaskDetail> { key ->
                TaskDetailScreen(viewModel { TaskDetailViewModel(key.id, app.tasks, app.projects) }, onBack = { backStack.removeLastOrNull() })
            }
            entry<Settings> {
                SettingsScreen(viewModel { SettingsViewModel(app.backups, app.contentResolver, app.autoBackup) }, onBack = { backStack.removeLastOrNull() })
            }
            entry<Search> {
                SearchScreen(
                    viewModel { SearchViewModel(app.tasks, app.projects, app.shopping, app.finance) },
                    onOpen = { target ->
                        when (target) {
                            is SearchTarget.Task -> backStack.add(TaskDetail(target.id))
                            is SearchTarget.Project -> backStack.add(ProjectDetail(target.id))
                            is SearchTarget.Debt -> backStack.add(DebtDetail(target.id))
                            is SearchTarget.Movement -> backStack.add(Finances(target.month.toString(), target.id))
                            is SearchTarget.ShoppingList -> {
                                // Compras is a tab of the main screen: go back to it with that list selected.
                                while (backStack.size > 1) backStack.removeLastOrNull()
                                tab = Tab.Shopping
                                listToOpen = target.id
                            }
                        }
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Finances> { key ->
                FinancesScreen(
                    viewModel { FinancesViewModel(app.finance, key.month?.let(YearMonth::parse) ?: YearMonth.now()) },
                    initialMovement = key.movementId,
                    onOpenDebt = { backStack.add(DebtDetail(it)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DebtDetail> { key ->
                DebtDetailScreen(viewModel { DebtDetailViewModel(key.id, app.finance) }, onBack = { backStack.removeLastOrNull() })
            }
            entry<ProjectDetail> { key ->
                ProjectDetailScreen(
                    viewModel { ProjectDetailViewModel(key.id, app.projects, app.tasks) },
                    onOpenTask = openTask,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}

@Composable
private fun MainScreen(
    tasksVm: TaskListViewModel,
    todayVm: TodayViewModel,
    projectsVm: ProjectsViewModel,
    shoppingVm: ShoppingViewModel,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFinances: () -> Unit,
    onSearch: () -> Unit,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    listToOpen: String?,
    onListOpened: () -> Unit,
    captureRequested: Boolean,
    onCaptureShown: () -> Unit,
) {
    LaunchedEffect(listToOpen) {
        if (listToOpen != null) {
            shoppingVm.select(listToOpen)
            onListOpened()
        }
    }
    var capturing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(captureRequested) {
        if (captureRequested) {
            capturing = true
            onCaptureShown()
        }
    }
    var addingItem by rememberSaveable { mutableStateOf(false) }
    val shopping by shoppingVm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.saved_to_inbox)
    val undoLabel = stringResource(R.string.undo)
    val drafts = rememberDrafts()
    /** A snackbar with "Deshacer" after deleting something. */
    val offerUndo: (String, () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            if (snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // In Compras the button adds items to the open list; everywhere else it captures a task.
            val shoppingTab = tab == Tab.Shopping
            if (!shoppingTab || shopping?.current != null) {
                AddButton(stringResource(if (shoppingTab) R.string.add_item else R.string.capture)) {
                    if (shoppingTab) addingItem = true else capturing = true
                }
            }
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach {
                    ShortNavigationBarItem(
                        selected = it == tab,
                        onClick = { onTabChange(it) },
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },
                        label = { CappedLabel(stringResource(it.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Today -> TodayScreen(todayVm, onOpenTask, onOpenSettings, onOpenFinances, onSearch, modifier)
            Tab.Inbox -> InboxScreen(tasksVm, onOpenTask, modifier)
            Tab.Tasks -> TasksScreen(tasksVm, onOpenTask, onSearch, modifier)
            Tab.Projects -> ProjectsScreen(projectsVm, onOpenProject, modifier)
            Tab.Shopping -> ShoppingScreen(shoppingVm, offerUndo, modifier)
        }
    }

    if (capturing) {
        CaptureSheet(
            drafts = drafts,
            onDismiss = { capturing = false },
            onSave = { title, notes ->
                tasksVm.capture(title, notes) { saved ->
                    if (saved) {
                        capturing = false
                        drafts.clear("capture.")
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    }
                }
            },
        )
    }

    val list = shopping?.current
    if (addingItem && list != null) {
        AddItemSheet(list.name, drafts, onDismiss = { addingItem = false }, onAdd = shoppingVm::add)
    }
}

