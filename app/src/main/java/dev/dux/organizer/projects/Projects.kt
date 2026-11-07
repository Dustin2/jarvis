package dev.dux.organizer.projects

import dev.dux.organizer.inbox.CaptureDetails

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.dux.organizer.data.TrashRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import dev.dux.organizer.tasks.TaskRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.dux.organizer.ui.NameDialog
import dev.dux.organizer.ui.AddButton
import dev.dux.organizer.ui.PlanRow
import dev.dux.organizer.ui.rememberDrafts
import dev.dux.organizer.inbox.CaptureSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.dux.organizer.ui.ScreenHeader
import androidx.compose.material3.DropdownMenuItem
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

class ProjectDetailViewModel(
    private val id: String,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val trash: TrashRepository,
) : ViewModel() {
    val project = projects.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val projectTasks = tasks.observeByProject(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(projects.rename(id, name)) }
    }

    fun setStatus(status: ProjectStatus) {
        viewModelScope.launch { projects.setStatus(id, status) }
    }

    /** To the trash with its tasks; restorable from there. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { trash.deleteProject(id); onDone() }
    }

    fun addTask(title: String, notes: String, details: CaptureDetails, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(tasks.capture(title, notes, projectId = id, dueDate = details.due, remindAt = details.remindAt)) }
    }

    /** Reopens a finished task, or completes an open one and hands back an undo for the "Deshacer" message. */
    fun toggleDone(task: Task, onCompleted: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            if (task.status == TaskStatus.DONE) tasks.setStatus(task.id, TaskStatus.TODO)
            else tasks.complete(task.id)?.let { undo -> onCompleted { viewModelScope.launch { undo() } } }
        }
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
fun ProjectsScreen(
    vm: ProjectsViewModel,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val items = summaries ?: return

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            ScreenHeader(R.string.tab_projects, onSearch, menu = { close ->
                DropdownMenuItem(text = { Text(stringResource(R.string.new_project)) }, onClick = { close(); creating = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = { close(); onOpenArchive() })
                DropdownMenuItem(text = { Text(stringResource(R.string.trash)) }, onClick = { close(); onOpenTrash() })
            })
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
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val drafts = rememberDrafts()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SavedIndicator(project?.updatedAt) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
                actions = {
                    if (project != null) IconButton(onClick = { deleting = true }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_project)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // Adds the task already in this project, instead of the Bandeja.
        floatingActionButton = { if (project != null) AddButton(stringResource(R.string.add_task)) { adding = true } },
    ) { padding ->
        val current = project ?: return@Scaffold
        val list = tasks ?: return@Scaffold
        if (deleting) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text(stringResource(R.string.delete_project_confirm, current.name)) },
                text = { Text(pluralStringResource(R.plurals.delete_project_body, list.size, list.size, TrashRepository.KEEP_DAYS)) },
                confirmButton = { TextButton(onClick = { deleting = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (adding) {
            CaptureSheet(
                drafts = drafts,
                heading = stringResource(R.string.add_task_to, current.name),
                draftKey = "project-task",
                onDismiss = { adding = false },
                onSave = { title, notes, details ->
                    vm.addTask(title, notes, details) { ok ->
                        if (ok) {
                            adding = false
                            drafts.clear("project-task.")
                        }
                    }
                },
            )
        }
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(bottom = 88.dp)) {
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
                TaskRow(task, onOpen = { onOpenTask(task.id) }, onToggleDone = {
                    vm.toggleDone(task) { undo ->
                        scope.launch {
                            val result = snackbar.showSnackbar(resources.getString(R.string.task_completed, task.title), undoLabel, duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) undo()
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun ProjectHeader(project: Project, vm: ProjectDetailViewModel) {
    // Same as the task detail: local text, saved instantly, never overwritten by the database.
    var name by rememberSaveable { mutableStateOf(project.name) }
    var error by rememberSaveable { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val nameLabel = stringResource(R.string.project_name)
    // Same look as the task detail (docs/design/task-detail.md): name without a box, options as rows.
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = name,
            onValueChange = { name = it; vm.rename(it) { ok -> error = !ok } },
            placeholder = { Text(nameLabel) },
            isError = error,
            supportingText = if (error) ({ Text(stringResource(R.string.title_invalid)) }) else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            PlanRow(R.drawable.ic_tasks, R.string.status, stringResource(project.status.label), isSet = true, onClick = { statusMenu = true }) {
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    ProjectStatus.entries.forEach { status ->
                        DropdownMenuItem(text = { Text(stringResource(status.label)) }, onClick = { statusMenu = false; vm.setStatus(status) })
                    }
                }
            }
        }
    }
}
