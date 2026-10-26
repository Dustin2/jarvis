package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = 'INBOX' ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<Task>>

    // Primero lo más prioritario; las fechas nulas al final; empate: lo más antiguo primero.
    @Query(
        """
        SELECT * FROM tasks WHERE status IN (:statuses)
        ORDER BY priority DESC, dueDate IS NULL, dueDate, createdAt
        """
    )
    fun observeByStatus(statuses: List<TaskStatus>): Flow<List<Task>>

    // Abiertas primero, luego terminadas; dentro de cada grupo, el mismo orden que la lista de tareas.
    @Query(
        """
        SELECT * FROM tasks WHERE projectId = :projectId
        ORDER BY status IN ('DONE', 'CANCELLED'), priority DESC, dueDate IS NULL, dueDate, createdAt
        """
    )
    fun observeByProject(projectId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    @Query("SELECT * FROM tasks ORDER BY createdAt")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE reminderAt IS NOT NULL")
    suspend fun withReminder(): List<Task>

    /** Solo borra el recordatorio si sigue siendo el mismo: no pisa ediciones hechas mientras sonaba. */
    @Query("UPDATE tasks SET reminderAt = NULL WHERE id = :id AND reminderAt = :at")
    suspend fun clearReminder(id: String, at: LocalDateTime)

    @Insert
    suspend fun insert(task: Task)

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}
