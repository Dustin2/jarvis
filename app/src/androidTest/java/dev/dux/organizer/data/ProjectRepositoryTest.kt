package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val projects = ProjectRepository(db.projects())
    private val tasks = TaskRepository(db.tasks())

    @After fun tearDown() = db.close()

    @Test fun seedsInitialProjectsOnlyOnce() = runBlocking {
        projects.seedIfEmpty()
        projects.seedIfEmpty()
        assertEquals(ProjectRepository.INITIAL_PROJECTS, projects.visible.first().map { it.name })
    }

    @Test fun createAppendsAndRejectsBlank() = runBlocking {
        projects.seedIfEmpty()
        assertTrue(projects.create("  Gimnasio "))
        assertFalse(projects.create("  "))
        assertEquals("Gimnasio", projects.visible.first().last().name)
    }

    @Test fun archivedProjectsAreHidden() = runBlocking {
        projects.create("Viejo")
        val id = projects.visible.first().single().id
        projects.setStatus(id, ProjectStatus.ARCHIVED)
        assertTrue(projects.visible.first().isEmpty())
    }

    @Test fun projectTasksListOpenFirst() = runBlocking {
        projects.create("Pro-Hielo")
        val projectId = projects.visible.first().single().id
        listOf("hecha", "abierta").forEach { title ->
            tasks.capture(title)
            val id = tasks.inbox.first().first { it.title == title }.id
            tasks.setProject(id, projectId)
            tasks.setDueDate(id, LocalDate.of(2026, 11, 1))
            if (title == "hecha") tasks.setStatus(id, TaskStatus.DONE)
        }
        val list = tasks.observeByProject(projectId).first()
        assertEquals(listOf("abierta", "hecha"), list.map { it.title })
        assertEquals(LocalDate.of(2026, 11, 1), list[0].dueDate)
    }
}
