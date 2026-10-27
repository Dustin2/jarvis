package dev.dux.organizer.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock

class ProjectRepository(private val dao: ProjectDao, private val clock: Clock = Clock.systemUTC()) {

    val visible: Flow<List<Project>> = dao.observeVisible()

    fun observe(id: String): Flow<Project?> = dao.observe(id)

    // shortcut: "empty table" = first run; holds while projects cannot be deleted (only archived).
    // If deleting is ever allowed, switch to a stored flag.
    suspend fun seedIfEmpty() {
        val now = clock.instant()
        dao.seedIfEmpty(
            INITIAL_PROJECTS.mapIndexed { i, name -> Project(name = name, sortOrder = i, createdAt = now, updatedAt = now) }
        )
    }

    /** Returns false if the name is not valid. */
    suspend fun create(name: String): Boolean {
        val clean = cleanTitle(name) ?: return false
        val now = clock.instant()
        dao.insert(Project(name = clean, sortOrder = dao.nextSortOrder(), createdAt = now, updatedAt = now))
        return true
    }

    suspend fun rename(id: String, name: String): Boolean {
        val clean = cleanTitle(name) ?: return false
        edit(id) { it.copy(name = clean) }
        return true
    }

    suspend fun setStatus(id: String, status: ProjectStatus) = edit(id) { it.copy(status = status) }

    private suspend fun edit(id: String, change: (Project) -> Project) {
        val current = dao.get(id) ?: return
        val changed = change(current)
        if (changed != current) dao.update(changed.copy(updatedAt = clock.instant()))
    }

    companion object {
        val INITIAL_PROJECTS = listOf("Pro-Hielo", "Desarrollo", "Estudios", "Personal")
    }
}
