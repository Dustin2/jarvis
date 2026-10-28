package dev.dux.organizer.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.tasks.TaskRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.dux.organizer.ui.Choice
import dev.dux.organizer.ui.NameDialog
import dev.dux.organizer.ui.SavedIndicator
import dev.dux.organizer.ui.asHeading

/** A project with its open tasks (already sorted: the first one is the next step). */
data class ProjectSummary(val project: Project, val open: List<Task>)

class ProjectsViewModel(private val projects: ProjectRepository, tasks: TaskRepository) : ViewModel() {
    val summaries = combine(projects.visible, tasks.open) { list, open ->
        val byProject = open.groupBy { it.projectId }
        list.map { ProjectSummary(it, byProject[it.id].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(projects.create(name)) }
    }
}

class ProjectDetailViewModel(private val id: String, private val projects: ProjectRepository, private val tasks: TaskRepository) : ViewModel() {
    val project = projects.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val projectTasks = tasks.observeByProject(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(projects.rename(id, name)) }
    }

    fun setStatus(status: ProjectStatus) {
        viewModelScope.launch { projects.setStatus(id, status) }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { tasks.setStatus(task.id, if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE) }
    }
}

@get:StringRes
val ProjectStatus.label: Int
    get() = when (this) {
        ProjectStatus.ACTIVE -> R.string.project_status_active
        ProjectStatus.PAUSED -> R.string.project_status_paused
        ProjectStatus.DONE -> R.string.project_status_done
        ProjectStatus.ARCHIVED -> R.string.project_status_archived
    }

@Composable
fun ProjectsScreen(vm: ProjectsViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val items = summaries ?: return

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_projects), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).asHeading())
                IconButton(onClick = { creating = true }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_project))
                }
            }
        }
        items(items, key = { it.project.id }) { (project, open) ->
            ListItem(
                onClick = { onOpen(project.id) },
                supportingContent = {
                    Text(
                        open.firstOrNull()?.let { stringResource(R.string.next_label, it.title) } ?: stringResource(R.string.no_next_step),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                overlineContent = if (project.status != ProjectStatus.ACTIVE) ({ Text(stringResource(project.status.label)) }) else null,
                trailingContent = { Text(pluralStringResource(R.plurals.open_count, open.size, open.size)) },
            ) { Text(project.name, style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (creating) {
        NameDialog(
            title = R.string.new_project,
            label = R.string.project_name,
            onDismiss = { creating = false },
            onConfirm = { name, done -> vm.create(name) { ok -> if (ok) creating = false; done(ok) } },
        )
    }
}

@Composable
fun ProjectDetailScreen(vm: ProjectDetailViewModel, onOpenTask: (String) -> Unit, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val tasks by vm.projectTasks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SavedIndicator(project?.updatedAt) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val current = project ?: return@Scaffold
        val list = tasks ?: return@Scaffold
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ProjectHeader(current, vm) }
            item {
                Text(
                    stringResource(R.string.project_tasks),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp).asHeading(),
                )
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.empty_project_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            items(list, key = { it.id }) { task ->
                TaskRow(task, onOpen = { onOpenTask(task.id) }, onToggleDone = { vm.toggleDone(task) })
            }
        }
    }
}

@Composable
private fun ProjectHeader(project: Project, vm: ProjectDetailViewModel) {
    // Same as the task detail: local text, saved instantly, never overwritten by the database.
    var name by rememberSaveable { mutableStateOf(project.name) }
    var error by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; vm.rename(it) { ok -> error = !ok } },
            label = { Text(stringResource(R.string.project_name)) },
            isError = error,
            supportingText = if (error) ({ Text(stringResource(R.string.title_invalid)) }) else null,
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Choice(R.string.status, ProjectStatus.entries, project.status, { it.label }, vm::setStatus)
    }
}
