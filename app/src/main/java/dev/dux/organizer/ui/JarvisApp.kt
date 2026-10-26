package dev.dux.organizer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.dux.organizer.R
import dev.dux.organizer.app
import dev.dux.organizer.inbox.CaptureSheet
import dev.dux.organizer.tasks.InboxScreen
import dev.dux.organizer.tasks.TaskDetailScreen
import dev.dux.organizer.tasks.TaskDetailViewModel
import dev.dux.organizer.tasks.TaskListViewModel
import dev.dux.organizer.tasks.TasksScreen
import dev.dux.organizer.today.TodayScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class TaskDetail(val id: String) : NavKey

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Today(R.string.tab_today, R.drawable.ic_today),
    Inbox(R.string.tab_inbox, R.drawable.ic_inbox),
    Tasks(R.string.tab_tasks, R.drawable.ic_tasks),
    Projects(R.string.tab_projects, R.drawable.ic_projects),
}

@Composable
fun JarvisApp() {
    // El back stack se guarda solo (rotación, proceso muerto); por eso las claves son @Serializable.
    val backStack = rememberNavBackStack(Main)
    val repo = LocalContext.current.app.tasks
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Cada pantalla tiene su propio estado guardado y sus propios ViewModels.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(viewModel { TaskListViewModel(repo) }, onOpenTask = { backStack.add(TaskDetail(it)) })
            }
            entry<TaskDetail> { key ->
                TaskDetailScreen(viewModel { TaskDetailViewModel(key.id, repo) }, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

@Composable
private fun MainScreen(vm: TaskListViewModel, onOpenTask: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var capturing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.saved_to_inbox)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { capturing = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.capture)) },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach {
                    ShortNavigationBarItem(
                        selected = it == tab,
                        onClick = { tab = it },
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },
                        label = { Text(stringResource(it.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Today -> TodayScreen(modifier)
            Tab.Inbox -> InboxScreen(vm, onOpenTask, modifier)
            Tab.Tasks -> TasksScreen(vm, onOpenTask, modifier)
            Tab.Projects -> EmptyTab(R.string.tab_projects, R.string.empty_projects, modifier)
        }
    }

    if (capturing) {
        CaptureSheet(
            onDismiss = { capturing = false },
            onSave = { title, notes ->
                vm.capture(title, notes) { saved ->
                    if (saved) {
                        capturing = false
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    }
                }
            },
        )
    }
}

@Composable
private fun EmptyTab(@StringRes title: Int, @StringRes message: Int, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
