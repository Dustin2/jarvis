package dev.dux.organizer.data

import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The only way tasks are written: the rules (validation, dates, statuses) live here.
 * [reminders] is null in data tests: no real alarms are scheduled there.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val clock: Clock = Clock.systemUTC(),
    private val reminders: Reminders? = null,
    /** Local date (not UTC) for recurrence: "today" must match the phone's calendar. */
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    val inbox: Flow<List<Task>> = dao.observeInbox()
    val pending: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.TODO))
    /** Inbox + to-do: what "Mi día" and the projects consider open. */
    val open: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.INBOX, TaskStatus.TODO))
    val finished: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.DONE, TaskStatus.CANCELLED))

    val all: Flow<List<Task>> = dao.observeAll()

    fun observe(id: String): Flow<Task?> = dao.observe(id)

    fun observeByProject(projectId: String): Flow<List<Task>> = dao.observeByProject(projectId)

    /**
     * Quick capture. Just a title goes to "Por ordenar" (the inbox); with a project, a date or a reminder it is
     * already sorted, so it goes straight to "Por hacer". Returns false if the title is not valid.
     * [id]: so the caller can undo it.
     */
    suspend fun capture(
        title: String,
        notes: String = "",
        projectId: String? = null,
        dueDate: LocalDate? = null,
        id: String = UUID.randomUUID().toString(),
        remindAt: LocalDateTime? = null,
    ): Boolean {
        val clean = cleanTitle(title) ?: return false
        val now = clock.instant()
        val sorted = projectId != null || dueDate != null || remindAt != null
        val task = Task(
            id = id, title = clean, notes = notes.trim().ifEmpty { null }, status = if (sorted) TaskStatus.TODO else TaskStatus.INBOX,
            projectId = projectId, dueDate = dueDate, reminderAt = remindAt, createdAt = now, updatedAt = now,
        )
        dao.insert(task)
        if (remindAt != null) reminders?.sync(task)
        return true
    }

    /** Returns the next occurrence it created, if finishing a recurring task created one. */
    suspend fun setStatus(id: String, status: TaskStatus): Task? = edit(id) { it.withStatus(status, clock.instant()) }

    /**
     * Completes a task and returns how to undo it ("Deshacer"): back to its previous status, and without the
     * next occurrence that completing a recurring task created (otherwise the series would have two open).
     */
    suspend fun complete(id: String): (suspend () -> Unit)? {
        val before = dao.get(id) ?: return null
        val next = setStatus(id, TaskStatus.DONE)
        return {
            setStatus(id, before.status)
            next?.let { deleteForever(it.id) }
        }
    }

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

    /** On a recurring task, the reminder time also goes into the rule so every occurrence keeps it. */
    suspend fun setReminder(id: String, at: LocalDateTime?) =
        edit(id) { it.copy(reminderAt = at, recurrence = it.recurrence?.copy(reminderTime = at?.toLocalTime())) }

    /**
     * Starts, changes or (with null) stops repeating. Repeating needs a date: a task without one gets the
     * rule's first occurrence as its scheduled date.
     */
    suspend fun setRecurrence(id: String, rule: Recurrence?) = edit(id) { task ->
        if (rule == null) return@edit task.copy(recurrence = null)
        val withTime = rule.copy(reminderTime = task.reminderAt?.toLocalTime() ?: task.recurrence?.reminderTime)
        task.copy(
            recurrence = withTime,
            seriesId = task.seriesId ?: task.id,
            scheduledDate = task.scheduledDate ?: if (task.dueDate == null) withTime.firstOnOrAfter(today()) else null,
        )
    }

    suspend fun setNotes(id: String, notes: String) = edit(id) { it.copy(notes = notes.ifBlank { null }) }

    /** To the trash: hidden everywhere, alarm cancelled, restorable from the trash. [at]: shared by a project's tasks. */
    suspend fun delete(id: String, at: Instant = clock.instant()) {
        edit(id) { it.copy(deletedAt = at) }
        reminders?.cancel(id)
    }

    /** Gone for good: emptying the trash, or undoing something just created. */
    suspend fun deleteForever(id: String) {
        dao.delete(id)
        reminders?.cancel(id)
    }

    /** "Deshacer" or the trash's "Restaurar": puts the task back as it was, reminder included. */
    suspend fun restore(task: Task) {
        val back = task.copy(deletedAt = null)
        dao.upsert(back)
        reminders?.sync(back)
    }

    // Serialized read-modify-write: typing fast in the detail screen must not let two edits overwrite each other.
    private val writeLock = Mutex()

    /** Returns the next occurrence it inserted, if any. */
    private suspend fun edit(id: String, change: (Task) -> Task): Task? = writeLock.withLock {
        val current = dao.get(id) ?: return@withLock null
        val changed = change(current)
        if (changed == current) return@withLock null
        val saved = changed.copy(updatedAt = clock.instant())
        val rule = saved.recurrence
        val next = if (rule != null && current.status in OPEN && saved.status !in OPEN) nextOccurrence(saved, rule) else null
        val nextInserted = if (next != null) dao.finishOccurrence(saved, next) else { dao.update(saved); false }
        // Completing, cancelling or changing the time decides whether the alarm stays; other fields do not matter to it.
        if (saved.reminderAt != current.reminderAt || saved.status != current.status) reminders?.sync(saved)
        if (nextInserted) reminders?.sync(next!!)
        if (nextInserted) next else null
    }

    /** The occurrence after [task]: next date per the rule, other date shifted by the same days, same reminder time. */
    private fun nextOccurrence(task: Task, rule: Recurrence): Task {
        val today = today()
        val anchor = task.dueDate ?: task.scheduledDate ?: today
        val nextAnchor = rule.nextAfter(anchor, maxOf(anchor, today))
        val shift = ChronoUnit.DAYS.between(anchor, nextAnchor)
        val now = clock.instant()
        return task.copy(
            id = UUID.randomUUID().toString(),
            status = TaskStatus.TODO,
            dueDate = task.dueDate?.plusDays(shift),
            scheduledDate = task.scheduledDate?.plusDays(shift),
            reminderAt = rule.reminderTime?.let { nextAnchor.atTime(it) },
            seriesId = task.seriesId ?: task.id,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
        )
    }

    private companion object {
        val OPEN = setOf(TaskStatus.INBOX, TaskStatus.TODO)
    }
}
