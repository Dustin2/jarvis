package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurringTasksTest {
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    private var today = LocalDate.of(2026, 10, 27)
    private val repo = TaskRepository(db.tasks(), today = { today })
    private val monthlyOn10 = Recurrence(Frequency.MONTHLY, monthDay = 10)

    @After fun tearDown() = db.close()

    private suspend fun all() = db.tasks().getAll()
    private suspend fun open() = all().filter { it.status == TaskStatus.INBOX || it.status == TaskStatus.TODO }

    /** "Pagar la luz", due on the 10th of each month. */
    private suspend fun bill(): String {
        repo.capture("Pagar la luz")
        val id = repo.inbox.first().single().id
        repo.setDueDate(id, LocalDate.of(2026, 11, 10))
        repo.setRecurrence(id, monthlyOn10)
        return id
    }

    @Test fun completingCreatesExactlyTheNextOccurrence() = runBlocking {
        val id = bill()
        repo.setStatus(id, TaskStatus.DONE)

        val next = open().single()
        assertEquals("Pagar la luz", next.title)
        assertEquals(LocalDate.of(2026, 12, 10), next.dueDate)
        assertEquals(TaskStatus.TODO, next.status)
        assertEquals(id, next.seriesId)
        assertEquals(monthlyOn10, next.recurrence)
        assertEquals(2, all().size)
    }

    @Test fun completingTwiceOrReopeningNeverDuplicates() = runBlocking {
        val id = bill()
        repo.setStatus(id, TaskStatus.DONE)
        repo.setStatus(id, TaskStatus.DONE)
        repo.setStatus(id, TaskStatus.TODO)
        repo.setStatus(id, TaskStatus.DONE)
        assertEquals(1, open().size)
        assertEquals(2, all().size)
    }

    @Test fun cancellingCreatesTheNextButDeletingDoesNot() = runBlocking {
        val id = bill()
        repo.setStatus(id, TaskStatus.CANCELLED)
        val next = open().single()
        repo.delete(next.id)
        assertEquals(0, open().size)
    }

    @Test fun lateCompletionSkipsMissedOccurrences() = runBlocking {
        val id = bill()
        today = LocalDate.of(2027, 1, 15)
        repo.setStatus(id, TaskStatus.DONE)
        assertEquals(LocalDate.of(2027, 2, 10), open().single().dueDate)
    }

    @Test fun stopRepeatingCreatesNothingMore() = runBlocking {
        val id = bill()
        repo.setRecurrence(id, null)
        repo.setStatus(id, TaskStatus.DONE)
        assertEquals(0, open().size)
    }

    @Test fun repeatingWithoutDateGetsTheFirstOccurrence() = runBlocking {
        repo.capture("Sacar la basura")
        val id = repo.inbox.first().single().id
        repo.setRecurrence(id, Recurrence(Frequency.WEEKLY, weekdays = setOf(java.time.DayOfWeek.FRIDAY)))
        // 2026-10-27 is a Tuesday: the first Friday is the 30th.
        assertEquals(LocalDate.of(2026, 10, 30), db.tasks().get(id)!!.scheduledDate)
    }

    @Test fun reminderTimeIsCarriedToTheNextOccurrence() = runBlocking {
        val id = bill()
        repo.setReminder(id, LocalDateTime.of(2026, 11, 10, 9, 0))
        assertEquals(LocalTime.of(9, 0), db.tasks().get(id)!!.recurrence!!.reminderTime)

        repo.setStatus(id, TaskStatus.DONE)
        assertEquals(LocalDateTime.of(2026, 12, 10, 9, 0), open().single().reminderAt)
    }

    @Test fun removingTheReminderRemovesItFromTheRule() = runBlocking {
        val id = bill()
        repo.setReminder(id, LocalDateTime.of(2026, 11, 10, 9, 0))
        repo.setReminder(id, null)
        repo.setStatus(id, TaskStatus.DONE)
        assertNull(open().single().reminderAt)
    }

    @Test fun undoingACompletionRemovesTheOccurrenceItCreated() = runBlocking {
        val id = bill()
        val undo = repo.complete(id)!!
        assertEquals(2, all().size)
        undo()
        assertEquals("only the original, open again", listOf(id), open().map { it.id })
        assertEquals(1, all().size)
    }
}
