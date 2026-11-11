package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private var now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val tasks = TaskRepository(db.tasks(), clock)
    private val projects = ProjectRepository(db.projects(), clock)
    private val finance = FinanceRepository(db.finance(), clock)
    private val trash = TrashRepository(db, tasks, finance, clock)

    @After fun tearDown() = db.close()

    private suspend fun projectId(name: String) = db.projects().getAll().single { it.name == name }.id

    @Test fun deletedTaskLeavesTheListsAndComesBack() = runBlocking {
        tasks.capture("Pagar la luz", id = "t")
        tasks.delete("t")

        assertTrue(tasks.open.first().isEmpty())
        assertEquals(listOf("t"), trash.trash.first().tasks.map { it.id })
        assertTrue(db.tasks().getAll().isEmpty()) // not in backups

        trash.restoreTask(trash.trash.first().tasks.single())
        assertEquals(listOf("t"), tasks.open.first().map { it.id })
        assertTrue(trash.trash.first().isEmpty)
    }

    @Test fun projectGoesWithItsTasksAndComesBackWithThem() = runBlocking {
        projects.create("Casa")
        val id = projectId("Casa")
        tasks.capture("Ya borrada antes", projectId = id, id = "old")
        tasks.delete("old")
        now = now.plusSeconds(60)
        tasks.capture("Pintar", projectId = id, id = "t")

        trash.deleteProject(id)

        assertTrue(projects.visible.first().isEmpty())
        assertTrue(tasks.open.first().isEmpty())
        val inTrash = trash.trash.first()
        assertEquals(listOf("t"), inTrash.projects.single().tasks.map { it.id })
        assertEquals(listOf("old"), inTrash.tasks.map { it.id }) // deleted on its own earlier: listed apart

        trash.restoreProject(id)
        assertEquals(listOf("Casa"), projects.visible.first().map { it.name })
        assertEquals(listOf("t"), tasks.open.first().map { it.id })
        assertEquals(listOf("old"), trash.trash.first().tasks.map { it.id })
    }

    @Test fun taskOfATrashedProjectComesBackWithoutIt() = runBlocking {
        projects.create("Casa")
        val id = projectId("Casa")
        tasks.capture("Pintar", projectId = id, id = "t")
        tasks.delete("t")
        now = now.plusSeconds(60)
        trash.deleteProject(id)

        trash.restoreTask(trash.trash.first().tasks.single())
        assertNull(tasks.open.first().single().projectId)
    }

    @Test fun deleteForeverEmptyAndPurge() = runBlocking {
        projects.create("Casa")
        val id = projectId("Casa")
        tasks.capture("Pintar", projectId = id, id = "t")
        trash.deleteProject(id)
        trash.deleteProjectForever(id)
        assertTrue(trash.trash.first().isEmpty)
        assertNull(db.tasks().get("t"))

        tasks.capture("Vieja", id = "a")
        tasks.delete("a")
        now = now.plusSeconds(TrashRepository.KEEP_DAYS * 86_400 - 60)
        tasks.capture("Nueva", id = "b")
        tasks.delete("b")
        now = now.plusSeconds(120)
        trash.purgeOld()
        assertEquals(listOf("b"), trash.trash.first().tasks.map { it.id })

        trash.empty()
        assertTrue(trash.trash.first().isEmpty)
    }

    @Test fun financeSitsInTheSameTrashAndIsPurgedWithIt() = runBlocking {
        finance.addDebt("Luis", DebtDirection.OWED_TO_ME, 5_000, null)
        finance.deleteDebt(finance.debts.first().single().debt.id)
        tasks.capture("Pagar la luz", id = "t")
        tasks.delete("t")
        assertEquals(listOf("Luis"), trash.trash.first().finance.map { it.label })

        now = now.plusSeconds(TrashRepository.KEEP_DAYS * 86_400 + 60)
        trash.purgeOld()
        assertTrue(trash.trash.first().isEmpty)
    }

    @Test fun emptyingTheTrashTakesFinanceToo() = runBlocking {
        finance.addDebt("Luis", DebtDirection.OWED_TO_ME, 5_000, null)
        finance.deleteDebt(finance.debts.first().single().debt.id)
        trash.empty()
        assertTrue(trash.trash.first().isEmpty)
        assertTrue(finance.debts.first().isEmpty())
    }
}
