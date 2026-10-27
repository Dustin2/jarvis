package dev.dux.organizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class TaskStatus { INBOX, TODO, DONE, CANCELLED }

/** [level] is stored so SQL can sort by priority. */
enum class Priority(val level: Int) { NONE(0), LOW(1), MEDIUM(2), HIGH(3) }

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("projectId"), Index("status")],
)
data class Task(
    // UUID, not autoincrement: two devices can create tasks without colliding when syncing.
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val status: TaskStatus = TaskStatus.INBOX,
    val priority: Priority = Priority.NONE,
    val projectId: String? = null,
    val dueDate: LocalDate? = null,
    val scheduledDate: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    /**
     * Local wall-clock time ("9:00"), not an instant: after a time zone change it still rings at 9:00.
     * Cleared when it fires; one reminder per task (schema version 2).
     */
    val reminderAt: LocalDateTime? = null,
)

/** Changes the status keeping [Task.completedAt] consistent: it only exists while the task is done. */
fun Task.withStatus(status: TaskStatus, now: Instant): Task = copy(
    status = status,
    updatedAt = now,
    completedAt = if (status == TaskStatus.DONE) completedAt ?: now else null,
)

const val MAX_TITLE_LENGTH = 200

/** Returns the title ready to save, or null if it is not valid. */
fun cleanTitle(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH }
