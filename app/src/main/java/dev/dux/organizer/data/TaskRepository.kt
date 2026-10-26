package dev.dux.organizer.data

import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** Única puerta de escritura de tareas: aquí viven las reglas (validación, fechas, estados). */
/** [reminders] es null en las pruebas de datos: ahí no se programan alarmas reales. */
class TaskRepository(
    private val dao: TaskDao,
    private val clock: Clock = Clock.systemUTC(),
    private val reminders: Reminders? = null,
) {

    val inbox: Flow<List<Task>> = dao.observeInbox()
    val pending: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.TODO))
    /** Bandeja + pendientes: lo que "Mi día" y los proyectos consideran abierto. */
    val open: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.INBOX, TaskStatus.TODO))
    val finished: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.DONE, TaskStatus.CANCELLED))

    fun observe(id: String): Flow<Task?> = dao.observe(id)

    fun observeByProject(projectId: String): Flow<List<Task>> = dao.observeByProject(projectId)

    /** Captura rápida a la Bandeja. Devuelve false si el título no es válido. */
    suspend fun capture(title: String, notes: String = ""): Boolean {
        val clean = cleanTitle(title) ?: return false
        val now = clock.instant()
        dao.insert(Task(title = clean, notes = notes.trim().ifEmpty { null }, createdAt = now, updatedAt = now))
        return true
    }

    suspend fun setStatus(id: String, status: TaskStatus) = edit(id) { it.withStatus(status, clock.instant()) }

    suspend fun setPriority(id: String, priority: Priority) = edit(id) { it.copy(priority = priority) }

    /** Devuelve false (y no guarda) si el título no es válido. */
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

    // Leer-modificar-escribir en serie: al escribir rápido en el detalle, dos ediciones no deben pisarse.
    private val writeLock = Mutex()

    private suspend fun edit(id: String, change: (Task) -> Task) = writeLock.withLock {
        val current = dao.get(id) ?: return@withLock
        val changed = change(current)
        if (changed == current) return@withLock
        val saved = changed.copy(updatedAt = clock.instant())
        dao.update(saved)
        // Completar, cancelar o cambiar la hora decide si la alarma sigue; el resto de campos no le importa.
        if (saved.reminderAt != current.reminderAt || saved.status != current.status) reminders?.sync(saved)
    }
}
