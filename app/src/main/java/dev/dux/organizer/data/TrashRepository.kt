package dev.dux.organizer.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A deleted project with the tasks that went to the trash with it. */
data class TrashedProject(val project: Project, val tasks: List<Task>)

data class Trash(val projects: List<TrashedProject>, val tasks: List<Task>, val finance: List<TrashedFinance> = emptyList()) {
    val isEmpty get() = projects.isEmpty() && tasks.isEmpty() && finance.isEmpty()
}

/**
 * Deleted projects and tasks: they wait here [KEEP_DAYS] days, can be restored, or deleted for good.
 * A project takes its tasks along (same deletedAt), so restoring it brings them all back.
 */
class TrashRepository(
    private val db: AppDatabase,
    private val tasks: TaskRepository,
    private val finance: FinanceRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val taskDao = db.tasks()
    private val projectDao = db.projects()

    val trash: Flow<Trash> = combine(projectDao.observeTrashed(), taskDao.observeTrashed(), finance.trashed) { projects, trashed, money ->
        val bundled = projects.associate { p -> p.id to trashed.filter { it.projectId == p.id && it.deletedAt == p.deletedAt } }
        Trash(projects.map { TrashedProject(it, bundled[it.id].orEmpty()) }, trashed - bundled.values.flatten().toSet(), money)
    }

    suspend fun deleteProject(id: String) = db.withTransaction {
        val project = projectDao.get(id) ?: return@withTransaction
        val now = clock.instant()
        projectDao.update(project.copy(deletedAt = now, updatedAt = now))
        taskDao.getByProject(id).forEach { tasks.delete(it.id, now) }
    }

    suspend fun restoreProject(id: String) = db.withTransaction {
        val project = projectDao.get(id) ?: return@withTransaction
        val at = project.deletedAt ?: return@withTransaction
        projectDao.update(project.copy(deletedAt = null, updatedAt = clock.instant()))
        taskDao.trashedWith(id, at).forEach { tasks.restore(it) }
    }

    suspend fun deleteProjectForever(id: String) = db.withTransaction {
        val project = projectDao.get(id) ?: return@withTransaction
        project.deletedAt?.let { taskDao.deleteTrashedWith(id, it) }
        projectDao.delete(id)
    }

    /** If its project is still in the trash, the task comes back without it (a link to a hidden project). */
    suspend fun restoreTask(task: Task) {
        val project = task.projectId?.let { projectDao.get(it) }
        tasks.restore(if (project?.deletedAt != null) task.copy(projectId = null) else task)
    }

    suspend fun deleteTaskForever(id: String) = tasks.deleteForever(id)

    suspend fun restoreFinance(item: TrashedFinance) = finance.restoreTrashed(item)

    suspend fun deleteFinanceForever(id: String) = finance.deleteTrashedForever(id)

    /** "Vaciar papelera". */
    suspend fun empty() = purgeBefore(clock.instant().plusSeconds(1))

    /** On every start: what has been in the trash longer than [KEEP_DAYS]. */
    suspend fun purgeOld() = purgeBefore(clock.instant().minus(Duration.ofDays(KEEP_DAYS)))

    // Tasks first: a project's go with it, and the rest must not lose their project to SET_NULL.
    private suspend fun purgeBefore(before: java.time.Instant) = db.withTransaction {
        taskDao.deleteTrashedBefore(before)
        projectDao.deleteTrashedBefore(before)
        finance.purgeTrashedBefore(before)
    }

    companion object {
        const val KEEP_DAYS = 30L
    }
}
