package dev.dux.organizer.data

import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The only way tasks are written: the rules (validation, dates, statuses) live here.
 * [reminders] is null in data tests: no real alarms are scheduled there.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val clock: Clock = Clock.systemUTC(),
    private val reminders: Reminders? = null,
) {

    val inbox: Flow<List<Task>> = dao.observeInbox()
    val pending: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.TODO))
    /** Inbox + to-do: what "Mi día" and the projects consider open. */
    val open: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.INBOX, TaskStatus.TODO))
    val finished: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.DONE, TaskStatus.CANCELLED))

    fun observe(id: String): Flow<Task?> = dao.observe(id)

    fun observeByProject(projectId: String): Flow<List<Task>> = dao.observeByProject(projectId)

    /** Quick capture to the Inbox. Returns false if the title is not valid. */
    suspend fun capture(title: String, notes: String = ""): Boolean {
        val clean = cleanTitle(title) ?: return false
        val now = clock.instant()
        dao.insert(Task(title = clean, notes = notes.trim().ifEmpty { null }, createdAt = now, updatedAt = now))
        return true
    }

    suspend fun setStatus(id: String, status: TaskStatus) = edit(id) { it.withStatus(status, clock.instant()) }

    suspend fun setPriority(id: String, priority: Priority) = edit(id) { it.copy(priority = priority) }

    /** Returns false (and saves nothing) if the title is not valid. */
    suspend fun rename(id: String, title: String): Boolean {
        val clean = cleanTitle(title) ?: return false
        edit(id) { it.copy(title = clean) }
        return true
    }

    suspend fun setDueDate(id: String, date: LocalDate?) = edit(id) { it.copy(dueDate = date) }

    suspend fun setScheduledDate(id: String, date: LocalDate?) = edit(id) { it.copy(scheduledDate = date) }

    suspend fun setProject(id: String, projectId: String?) = edit(id) { it.copy(projectId = projectId) }

    suspend fun setReminder(id: String, at: LocalDateTime?) = edit(id) { it.copy(reminderAt = at) }

    suspend fun setNotes(id: String, notes: String) = edit(id) { it.copy(notes = notes.ifBlank { null }) }

    suspend fun delete(id: String) {
        dao.delete(id)
        reminders?.cancel(id)
    }

    // Serialized read-modify-write: typing fast in the detail screen must not let two edits overwrite each other.
    private val writeLock = Mutex()

    private suspend fun edit(id: String, change: (Task) -> Task) = writeLock.withLock {
        val current = dao.get(id) ?: return@withLock
        val changed = change(current)
        if (changed == current) return@withLock
        val saved = changed.copy(updatedAt = clock.instant())
        dao.update(saved)
        // Completing, cancelling or changing the time decides whether the alarm stays; other fields do not matter to it.
        if (saved.reminderAt != current.reminderAt || saved.status != current.status) reminders?.sync(saved)
    }
}
