package dev.dux.organizer.trash

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.R
import dev.dux.organizer.data.Project
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.ProjectStatus
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TrashRepository
import dev.dux.organizer.ui.asHeading
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(private val repo: TrashRepository) : ViewModel() {
    val trash = repo.trash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun restoreProject(id: String) = launch { repo.restoreProject(id) }
    fun deleteProjectForever(id: String) = launch { repo.deleteProjectForever(id) }
    fun restoreTask(task: Task) = launch { repo.restoreTask(task) }
    fun deleteTaskForever(id: String) = launch { repo.deleteTaskForever(id) }
    fun empty() = launch { repo.empty() }
}

class ArchiveViewModel(private val projects: ProjectRepository) : ViewModel() {
    val archived = projects.archived.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unarchive(id: String) {
        viewModelScope.launch { projects.setStatus(id, ProjectStatus.ACTIVE) }
    }
}

/** What was deleted, for [TrashRepository.KEEP_DAYS] days: restore it or delete it for good. */
@Composable
fun TrashScreen(vm: TrashViewModel, onBack: () -> Unit) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    var emptying by rememberSaveable { mutableStateOf(false) }
    /** Something to delete for good, waiting for confirmation. */
    var forever by remember { mutableStateOf<(() -> Unit)?>(null) }

    SecondaryScreen(R.string.trash, onBack, actions = {
        if (trash?.isEmpty == false) TextButton(onClick = { emptying = true }) { Text(stringResource(R.string.trash_empty)) }
    }) { padding ->
        val current = trash ?: return@SecondaryScreen
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Explanation(pluralStringResource(R.plurals.trash_explanation, TrashRepository.KEEP_DAYS.toInt(), TrashRepository.KEEP_DAYS)) }
            if (current.isEmpty) item { Explanation(stringResource(R.string.trash_nothing)) }
            if (current.projects.isNotEmpty()) heading(R.string.tab_projects)
            items(current.projects, key = { "p" + it.project.id }) { (project, tasks) ->
                TrashRow(
                    project.name,
                    pluralStringResource(R.plurals.trash_project_tasks, tasks.size, tasks.size),
                    onRestore = { vm.restoreProject(project.id) },
                    onDelete = { forever = { vm.deleteProjectForever(project.id) } },
                )
            }
            if (current.tasks.isNotEmpty()) heading(R.string.tab_tasks)
            items(current.tasks, key = { "t" + it.id }) { task ->
                TrashRow(task.title, null, onRestore = { vm.restoreTask(task) }, onDelete = { forever = { vm.deleteTaskForever(task.id) } })
            }
        }
    }
    if (emptying) {
        ConfirmForever(R.string.trash_empty_confirm, onDismiss = { emptying = false }) { vm.empty() }
    }
    forever?.let { delete ->
        ConfirmForever(R.string.trash_delete_confirm, onDismiss = { forever = null }, onConfirm = delete)
    }
}

/** Archived projects: out of the Proyectos list, nothing lost. Tap to open, or bring one back. */
@Composable
fun ArchiveScreen(vm: ArchiveViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val archived by vm.archived.collectAsStateWithLifecycle()
    SecondaryScreen(R.string.archive, onBack) { padding ->
        val list = archived ?: return@SecondaryScreen
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Explanation(stringResource(if (list.isEmpty()) R.string.archive_nothing else R.string.archive_explanation)) }
            items(list, key = Project::id) { project ->
                ListItem(
                    onClick = { onOpen(project.id) },
                    trailingContent = { TextButton(onClick = { vm.unarchive(project.id) }) { Text(stringResource(R.string.unarchive)) } },
                ) { Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryScreen(
    @StringRes title: Int,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
                actions = actions,
            )
        },
        content = content,
    )
}

@Composable
private fun Explanation(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

private fun LazyListScope.heading(@StringRes title: Int) = item {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).asHeading(),
    )
}

@Composable
private fun TrashRow(name: String, detail: String?, onRestore: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = {
            Row {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
                IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_forever, name)) }
            }
        },
    ) { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ConfirmForever(@StringRes title: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(R.string.delete_confirm_body)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
