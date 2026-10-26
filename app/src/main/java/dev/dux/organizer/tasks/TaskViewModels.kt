package dev.dux.organizer.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.ProjectRepository
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** null = todavía cargando (distinto de lista vacía). */
private fun <T> Flow<T>.state(vm: ViewModel): StateFlow<T?> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {
    val inbox = repo.inbox.state(this)
    val lists = combine(repo.pending, repo.finished) { pending, finished -> pending to finished }.state(this)

    fun capture(title: String, notes: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.capture(title, notes)) }
    }

    fun setStatus(id: String, status: TaskStatus) {
        viewModelScope.launch { repo.setStatus(id, status) }
    }
}

class TaskDetailViewModel(
    private val id: String,
    private val repo: TaskRepository,
    projectRepo: ProjectRepository,
) : ViewModel() {
    val projects = projectRepo.visible.state(this)

    /** Emite null también si la tarea fue borrada. */
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
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repo.delete(id); onDone() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
