package dev.dux.organizer.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** null = still loading (different from an empty list). */
private fun <T> Flow<T>.state(vm: ViewModel): StateFlow<T?> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

class TaskListViewModel(private val repo: TaskRepository, projects: ProjectRepository) : ViewModel() {
    val inbox = repo.inbox.state(this)
    val projectNames = projects.visible.map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val lists = combine(repo.pending, repo.finished) { pending, finished -> pending to finished }.state(this)

    fun capture(title: String, notes: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.capture(title, notes)) }
    }

    fun setStatus(id: String, status: TaskStatus) {
        viewModelScope.launch { repo.setStatus(id, status) }
    }

    /** Completes the task and hands back an undo for the "Deshacer" message. */
    fun complete(task: Task, onCompleted: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            repo.complete(task.id)?.let { undo -> onCompleted { viewModelScope.launch { undo() } } }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { repo.delete(task.id) }
    }

    fun restore(task: Task) {
        viewModelScope.launch { repo.restore(task) }
    }
}

class TaskDetailViewModel(
    private val id: String,
    private val repo: TaskRepository,
    projectRepo: ProjectRepository,
) : ViewModel() {
    val projects = projectRepo.visible.state(this)

    /** Also emits null if the task was deleted. */
    val task: StateFlow<Task?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(title: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.rename(id, title)) }
    }

    fun setNotes(notes: String) = launch { repo.setNotes(id, notes) }
    fun setStatus(status: TaskStatus) = launch { repo.setStatus(id, status) }
    fun setPriority(priority: Priority) = launch { repo.setPriority(id, priority) }
    fun setDueDate(date: LocalDate?) = launch { repo.setDueDate(id, date) }
    fun setScheduledDate(date: LocalDate?) = launch { repo.setScheduledDate(id, date) }
    fun setProject(projectId: String?) = launch { repo.setProject(id, projectId) }
    fun setReminder(at: LocalDateTime?) = launch { repo.setReminder(id, at) }
    fun setRecurrence(rule: Recurrence?) = launch { repo.setRecurrence(id, rule) }
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repo.delete(id); onDone() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
