package dev.dux.organizer.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.ui.ChoiceChips
import dev.dux.organizer.ui.ScreenHeader

/** The Bandeja is the first step of a task, so it lives here as a view (docs/design/navigation.md). */
enum class TaskView(@StringRes val label: Int) {
    Inbox(R.string.view_inbox),
    Pending(R.string.view_pending),
    Finished(R.string.view_finished),
}

@Composable
/** [offerUndo] shows a message with "Deshacer" after completing or deleting from the Bandeja. */
fun TasksScreen(
    vm: TaskListViewModel,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    offerUndo: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val projectNames by vm.projectNames.collectAsStateWithLifecycle()
    val inboxTasks = inbox ?: return
    val (pending, finished) = lists ?: return
    var view by rememberSaveable { mutableStateOf(TaskView.Pending) }
    val counts = mapOf(TaskView.Inbox to inboxTasks.size, TaskView.Pending to pending.size, TaskView.Finished to finished.size)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { ScreenHeader(R.string.tab_tasks, onSearch) }
        item {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                ChoiceChips(TaskView.entries, view, { stringResource(it.label, counts.getValue(it)) }) { view = it }
            }
        }
        when (view) {
            TaskView.Inbox -> {
                if (inboxTasks.isEmpty()) emptyMessage(R.string.empty_inbox)
                items(inboxTasks, key = { it.id }) { task ->
                    val delete = {
                        vm.delete(task)
                        offerUndo(resources.getString(R.string.task_deleted, task.title)) { vm.restore(task) }
                    }
                    SwipeToDelete(onDelete = delete) {
                        TaskRow(
                            task,
                            onOpen = { onOpen(task.id) },
                            onToggleDone = {
                                vm.setStatus(task.id, TaskStatus.DONE)
                                offerUndo(resources.getString(R.string.task_completed, task.title)) { vm.setStatus(task.id, TaskStatus.INBOX) }
                            },
                            trailing = {
                                TextButton(onClick = { vm.setStatus(task.id, TaskStatus.TODO) }) { Text(stringResource(R.string.plan)) }
                            },
                        )
                    }
                }
            }
            TaskView.Pending -> {
                if (pending.isEmpty()) emptyMessage(R.string.empty_tasks)
                items(pending, key = { it.id }) { task ->
                    TaskRow(task, onOpen = { onOpen(task.id) }, onToggleDone = { vm.setStatus(task.id, TaskStatus.DONE) }, projectName = projectNames[task.projectId])
                }
            }
            TaskView.Finished -> {
                if (finished.isEmpty()) emptyMessage(R.string.empty_finished)
                items(finished, key = { it.id }) { task ->
                    TaskRow(task, onOpen = { onOpen(task.id) }, onToggleDone = { vm.setStatus(task.id, TaskStatus.TODO) }, projectName = projectNames[task.projectId])
                }
            }
        }
    }
}

/**
 * Swipe towards the start to delete. Swiping is invisible to TalkBack, so the row also gets an
 * "Eliminar" accessibility action.
 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // remember, not rememberSaveable: a row brought back by "Deshacer" must not come back already dismissed.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val label = stringResource(R.string.delete)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true }) },
        backgroundContent = {
            // Only while swiping: at rest the red would show through the row's rounded corners.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(label, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

private fun LazyListScope.emptyMessage(@StringRes message: Int) = item {
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
