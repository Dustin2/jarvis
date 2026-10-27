package dev.dux.organizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class ProjectStatus { ACTIVE, PAUSED, DONE, ARCHIVED }

// The table exists since version 1 because Task references it; the projects UI arrived in milestone 3.
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
