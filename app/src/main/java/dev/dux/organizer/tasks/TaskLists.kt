package dev.dux.organizer.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import dev.dux.organizer.ui.asHeading

@Composable
fun InboxScreen(vm: TaskListViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val items = inbox ?: return
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        header(R.string.tab_inbox)
        if (items.isEmpty()) emptyMessage(R.string.empty_inbox)
        items(items, key = { it.id }) { task ->
            TaskRow(
                task,
                onOpen = { onOpen(task.id) },
                onToggleDone = null,
                trailing = {
                    TextButton(onClick = { vm.setStatus(task.id, TaskStatus.TODO) }) {
                        Text(stringResource(R.string.plan))
                    }
                },
            )
        }
    }
}

@Composable
fun TasksScreen(vm: TaskListViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val (pending, finished) = lists ?: return
    var showFinished by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        header(R.string.tab_tasks)
        if (pending.isEmpty()) emptyMessage(R.string.empty_tasks)
        items(pending, key = { it.id }) { task ->
            TaskRow(task, onOpen = { onOpen(task.id) }, onToggleDone = { vm.setStatus(task.id, TaskStatus.DONE) })
        }
        if (finished.isNotEmpty()) {
            item {
                FilterChip(
                    selected = showFinished,
                    onClick = { showFinished = !showFinished },
                    label = { Text(stringResource(R.string.show_finished, finished.size)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (showFinished) {
                items(finished, key = { it.id }) { task ->
                    TaskRow(task, onOpen = { onOpen(task.id) }, onToggleDone = { vm.setStatus(task.id, TaskStatus.TODO) })
                }
            }
        }
    }
}

private fun LazyListScope.header(@StringRes title: Int) = item {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).asHeading(),
    )
}

private fun LazyListScope.emptyMessage(@StringRes message: Int) = item {
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
