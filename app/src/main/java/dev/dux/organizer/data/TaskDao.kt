package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}
