package dev.dux.organizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TaskStatus { INBOX, TODO, DONE, CANCELLED }

/** [level] se guarda en la base para poder ordenar por prioridad en SQL. */
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
    // UUID y no autoincremento: dos dispositivos pueden crear tareas sin chocar al sincronizar.
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
)

/** Cambia el estado manteniendo [Task.completedAt] coherente: solo existe mientras la tarea está hecha. */
fun Task.withStatus(status: TaskStatus, now: Instant): Task = copy(
    status = status,
    updatedAt = now,
    completedAt = if (status == TaskStatus.DONE) completedAt ?: now else null,
)

const val MAX_TITLE_LENGTH = 200

/** Devuelve el título listo para guardar, o null si no es válido. */
fun cleanTitle(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH }
