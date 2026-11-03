package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = 'INBOX' AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<Task>>

    // Highest priority first; null dates last; ties: oldest first.
    @Query(
        """
        SELECT * FROM tasks WHERE status IN (:statuses) AND deletedAt IS NULL
        ORDER BY priority DESC, dueDate IS NULL, dueDate, createdAt
        """
    )
    fun observeByStatus(statuses: List<TaskStatus>): Flow<List<Task>>

    // Open first, then finished; within each group, the same order as the task list.
    @Query(
        """
        SELECT * FROM tasks WHERE projectId = :projectId AND deletedAt IS NULL
        ORDER BY status IN ('DONE', 'CANCELLED'), priority DESC, dueDate IS NULL, dueDate, createdAt
        """
    )
    fun observeByProject(projectId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    // shortcut: the trash is not in backups; add it to the backup format if users ask to keep it.
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND deletedAt IS NULL")
    suspend fun getByProject(projectId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<Task>>

    /** The tasks that went to the trash with their project (same instant). */
    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND deletedAt = :at")
    suspend fun trashedWith(projectId: String, at: Instant): List<Task>

    @Query("DELETE FROM tasks WHERE projectId = :projectId AND deletedAt = :at")
    suspend fun deleteTrashedWith(projectId: String, at: Instant)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deleteTrashedBefore(before: Instant)

    @Query("SELECT * FROM tasks WHERE reminderAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun withReminder(): List<Task>

    /** Only clears the reminder if it is still the same one: does not overwrite edits made while it fired. */
    @Query("UPDATE tasks SET reminderAt = NULL WHERE id = :id AND reminderAt = :at")
    suspend fun clearReminder(id: String, at: LocalDateTime)

    @Insert
    suspend fun insert(task: Task)

    @Upsert
    suspend fun upsert(task: Task)

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tasks WHERE seriesId = :seriesId AND id != :exceptId AND status IN ('INBOX', 'TODO') AND deletedAt IS NULL")
    suspend fun countOpenInSeries(seriesId: String, exceptId: String): Int

    /**
     * Saves a finished occurrence and inserts the next one, in one transaction, only if the series has no
     * other open occurrence: never two open tasks for the same rule. Returns whether [next] was inserted.
     */
    @Transaction
    suspend fun finishOccurrence(finished: Task, next: Task): Boolean {
        update(finished)
        if (countOpenInSeries(next.seriesId!!, finished.id) > 0) return false
        insert(next)
        return true
    }

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}
