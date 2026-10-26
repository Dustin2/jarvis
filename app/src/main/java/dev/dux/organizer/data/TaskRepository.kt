package dev.dux.organizer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** Única puerta de escritura de tareas: aquí viven las reglas (validación, fechas, estados). */
class TaskRepository(private val dao: TaskDao, private val clock: Clock = Clock.systemUTC()) {

    val inbox: Flow<List<Task>> = dao.observeInbox()
    val pending: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.TODO))
    val finished: Flow<List<Task>> = dao.observeByStatus(listOf(TaskStatus.DONE, TaskStatus.CANCELLED))

    fun observe(id: String): Flow<Task?> = dao.observe(id)

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

    suspend fun setNotes(id: String, notes: String) = edit(id) { it.copy(notes = notes.ifBlank { null }) }

    suspend fun delete(id: String) = dao.delete(id)

    // Leer-modificar-escribir en serie: al escribir rápido en el detalle, dos ediciones no deben pisarse.
    private val writeLock = Mutex()

    private suspend fun edit(id: String, change: (Task) -> Task) = writeLock.withLock {
        val current = dao.get(id) ?: return@withLock
        val changed = change(current)
        if (changed != current) dao.update(changed.copy(updatedAt = clock.instant()))
    }
}
