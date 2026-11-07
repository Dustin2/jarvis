package dev.dux.organizer.tasks

import dev.dux.organizer.ui.SectionTitle
import java.time.LocalDate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import dev.dux.organizer.ui.blockRowShapes
import dev.dux.organizer.ui.inBlock
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
import dev.dux.organizer.data.Task
import androidx.compose.ui.res.pluralStringResource
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.ui.ChoiceChips
import dev.dux.organizer.ui.ScreenHeader


/** "Por ordenar" (the inbox) is the first step of a task, so it lives here as a view (docs/design/navigation.md). */
enum class TaskView(@StringRes val label: Int) {
    Inbox(R.string.view_inbox),
    Pending(R.string.view_pending),
    Finished(R.string.view_finished),
}

@Composable
/** [offerUndo] shows a message with "Deshacer" after completing a task or deleting one from the Bandeja. */
fun TasksScreen(
    vm: TaskListViewModel,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    offerUndo: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val projectNames by vm.projectNames.collectAsStateWithLifecycle()
    val inboxTasks = inbox ?: return
    val (pending, finished) = lists ?: return
    var view by rememberSaveable { mutableStateOf(TaskView.Pending) }
    val counts = mapOf(TaskView.Inbox to inboxTasks.size, TaskView.Pending to pending.size, TaskView.Finished to finished.size)

    val delete: (Task) -> Unit = { task ->
        vm.delete(task)
        offerUndo(resources.getString(R.string.task_deleted, task.title)) { vm.restore(task) }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { ScreenHeader(R.string.tab_tasks, onSearch, badge = (inboxTasks.size + pending.size).let { pluralStringResource(R.plurals.open_count, it, it) }) }
        item {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                ChoiceChips(TaskView.entries, view, { stringResource(it.label, counts.getValue(it)) }) { view = it }
            }
        }
        when (view) {
            TaskView.Inbox -> {
                if (inboxTasks.isEmpty()) emptyMessage(R.string.empty_inbox)
                itemsIndexed(inboxTasks, key = { _, task -> task.id }) { index, task ->
                    // animateItem: when a row leaves, the ones below slide up instead of jumping.
                    TaskRow(
                        task,
                        onOpen = { onOpen(task.id) },
                        onToggleDone = { vm.complete(task) { undo -> offerUndo(resources.getString(R.string.task_completed, task.title), undo) } },
                        modifier = Modifier.animateItem().inBlock(index, inboxTasks.lastIndex),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        shapes = blockRowShapes(),
                        trailing = {
                            TextButton(onClick = { vm.setStatus(task.id, TaskStatus.TODO) }) { Text(stringResource(R.string.plan)) }
                        },
                        onDelete = { delete(task) },
                    )
                }
            }
            TaskView.Pending -> {
                if (pending.isEmpty()) emptyMessage(R.string.empty_tasks)
                for ((group, tasks) in groupByDate(pending, LocalDate.now())) {
                    item(key = group.name) { Box(Modifier.animateItem()) { SectionTitle(stringResource(group.label)) } }
                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                        TaskRow(
                            task,
                            onOpen = { onOpen(task.id) },
                            onToggleDone = { vm.complete(task) { undo -> offerUndo(resources.getString(R.string.task_completed, task.title), undo) } },
                            projectName = projectNames[task.projectId],
                            modifier = Modifier.animateItem().inBlock(index, tasks.lastIndex),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shapes = blockRowShapes(),
                            onDelete = { delete(task) },
                        )
                    }
                }
            }
            TaskView.Finished -> {
                if (finished.isEmpty()) emptyMessage(R.string.empty_finished)
                itemsIndexed(finished, key = { _, task -> task.id }) { index, task ->
                    TaskRow(
                        task,
                        onOpen = { onOpen(task.id) },
                        onToggleDone = { vm.setStatus(task.id, TaskStatus.TODO) },
                        projectName = projectNames[task.projectId],
                        modifier = Modifier.animateItem().inBlock(index, finished.lastIndex),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        shapes = blockRowShapes(),
                        onDelete = { delete(task) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.emptyMessage(@StringRes message: Int) = item {
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
