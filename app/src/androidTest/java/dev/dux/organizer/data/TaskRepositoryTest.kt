package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "repository-test.db"
    private var db = open()
    private var repo = TaskRepository(db.tasks())

    // A real file (not in memory) so it can be closed and reopened, like restarting the app.
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test fun captureGoesToInboxAndRejectsBlank() = runBlocking {
        assertTrue(repo.capture("  Llamar al proveedor ", "  "))
        assertFalse(repo.capture("   "))

        val inbox = repo.inbox.first()
        assertEquals(1, inbox.size)
        assertEquals("Llamar al proveedor", inbox[0].title)
        assertNull(inbox[0].notes)
    }

    @Test fun captureWithADetailIsAlreadySorted() = runBlocking {
        val tomorrow = java.time.LocalDate.now().plusDays(1)
        repo.capture("Pagar la luz", dueDate = tomorrow, id = "due")
        repo.capture("Llamar a mamá", remindAt = tomorrow.atTime(9, 0), id = "reminder")
        repo.capture("Solo el título", id = "plain")

        assertEquals(listOf("plain"), repo.inbox.first().map { it.id })
        val todo = repo.pending.first().associateBy { it.id }
        assertEquals(setOf("due", "reminder"), todo.keys)
        assertEquals(tomorrow, todo.getValue("due").dueDate)
        assertEquals(tomorrow.atTime(9, 0), todo.getValue("reminder").reminderAt)
    }

    @Test fun captureWithPriorityAndRepeatStartsTheSeries() = runBlocking {
        repo.capture("Regar plantas", priority = Priority.HIGH, recurrence = Recurrence(Frequency.DAILY), id = "plants")

        val task = repo.pending.first().single()
        assertEquals(Priority.HIGH, task.priority)
        assertEquals(Frequency.DAILY, task.recurrence?.frequency)
        assertEquals("plants", task.seriesId)
        // A repeating task without a date gets its first day, as when repeat is chosen in the detail.
        assertEquals(java.time.LocalDate.now(), task.scheduledDate)
    }

    @Test fun postponeMovesEarlierDatesToTheNewDay() = runBlocking {
        val today = java.time.LocalDate.now()
        val later = today.plusDays(3).atTime(10, 0)
        repo.capture("Vencida", dueDate = today.minusDays(1), id = "overdue")
        repo.capture("Más adelante", dueDate = today.plusDays(10), id = "far")
        repo.capture("Sin fecha", id = "none")

        listOf("overdue", "far", "none").forEach { repo.postpone(it, later) }

        val all = repo.all.first().associateBy { it.id }
        assertEquals(later.toLocalDate(), all.getValue("overdue").dueDate)
        assertEquals(today.plusDays(10), all.getValue("far").dueDate) // already later: stays
        assertNull(all.getValue("none").dueDate)
        assertTrue(all.values.all { it.reminderAt == later })
    }

    @Test fun planCompleteAndReopen() = runBlocking {
        repo.capture("Revisar facturas")
        val id = repo.inbox.first().single().id

        repo.setStatus(id, TaskStatus.TODO)
        assertTrue(repo.inbox.first().isEmpty())
        assertEquals(id, repo.pending.first().single().id)

        repo.setStatus(id, TaskStatus.DONE)
        assertNotNull(repo.finished.first().single().completedAt)

        repo.setStatus(id, TaskStatus.TODO)
        assertNull(repo.pending.first().single().completedAt)
    }

    @Test fun pendingIsSortedByPriority() = runBlocking {
        listOf("baja" to Priority.LOW, "alta" to Priority.HIGH, "ninguna" to Priority.NONE).forEach { (title, priority) ->
            repo.capture(title)
            val id = repo.inbox.first().first { it.title == title }.id
            repo.setStatus(id, TaskStatus.TODO)
            repo.setPriority(id, priority)
        }
        assertEquals(listOf("alta", "baja", "ninguna"), repo.pending.first().map { it.title })
    }

    @Test fun invalidRenameKeepsPreviousTitle() = runBlocking {
        repo.capture("Original")
        val id = repo.inbox.first().single().id
        assertFalse(repo.rename(id, "  "))
        assertEquals("Original", repo.observe(id).first()?.title)
    }

    @Test fun dataSurvivesReopeningTheDatabase() = runBlocking {
        repo.capture("Persistente", "con notas")
        db.close()

        db = open()
        repo = TaskRepository(db.tasks())
        val task = repo.inbox.first().single()
        assertEquals("Persistente", task.title)
        assertEquals("con notas", task.notes)
    }

    @Test fun deleteMovesToTrashAndDeleteForeverRemoves() = runBlocking {
        repo.capture("Borrar")
        val id = repo.inbox.first().single().id
        repo.delete(id)
        assertTrue(repo.all.first().isEmpty())
        assertNotNull(repo.observe(id).first()?.deletedAt)
        repo.deleteForever(id)
        assertNull(repo.observe(id).first())
    }

    @Test fun deletedTaskCanBeRestoredAsItWas() = runBlocking {
        repo.capture("Idea", "con notas")
        val task = repo.inbox.first().single()
        repo.delete(task.id)
        assertTrue(repo.inbox.first().isEmpty())
        repo.restore(task)
        assertEquals(task, repo.inbox.first().single())
    }
}
