package dev.dux.organizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class ProjectStatus { ACTIVE, PAUSED, DONE, ARCHIVED }

// La tabla existe desde la versión 1 porque Task la referencia; la UI de proyectos llega en el Hito 3.
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorKey: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
