package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE status != 'ARCHIVED' ORDER BY sortOrder, name")
    fun observeVisible(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): Project?

    @Query("SELECT * FROM projects ORDER BY sortOrder")
    suspend fun getAll(): List<Project>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM projects")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(project: Project)

    @Insert
    suspend fun insertAll(projects: List<Project>)

    @Update
    suspend fun update(project: Project)

    /** Inserts the initial projects only if the table is empty; in a transaction to avoid duplicates. */
    @Transaction
    suspend fun seedIfEmpty(projects: List<Project>) {
        if (count() == 0) insertAll(projects)
    }
}
