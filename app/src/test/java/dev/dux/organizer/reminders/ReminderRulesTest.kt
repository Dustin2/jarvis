package dev.dux.organizer.reminders

import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ReminderRulesTest {
    private val now = LocalDateTime.of(2026, 10, 26, 9, 0)
    private val t = Instant.parse("2026-10-26T15:00:00Z")
    private fun task(at: LocalDateTime?, status: TaskStatus = TaskStatus.TODO) =
        Task(title = "x", status = status, reminderAt = at, createdAt = t, updatedAt = t)

    @Test fun futureReminderIsScheduled() {
        val at = now.plusHours(2)
        assertEquals(ReminderAction.Schedule(at), reminderAction(task(at), now))
    }

    @Test fun dueOrMissedReminderNotifiesNow() {
        assertEquals(ReminderAction.NotifyNow, reminderAction(task(now), now))
        assertEquals(ReminderAction.NotifyNow, reminderAction(task(now.minusDays(1)), now))
        // Android puede disparar unos segundos antes: no reprogramar por eso.
        assertEquals(ReminderAction.NotifyNow, reminderAction(task(now.plusSeconds(10)), now))
    }

    @Test fun inboxTasksAlsoRemind() {
        assertEquals(ReminderAction.NotifyNow, reminderAction(task(now, TaskStatus.INBOX), now))
    }

    @Test fun finishedDeletedOrWithoutReminderCancels() {
        assertEquals(ReminderAction.Cancel, reminderAction(task(now, TaskStatus.DONE), now))
        assertEquals(ReminderAction.Cancel, reminderAction(task(now, TaskStatus.CANCELLED), now))
        assertEquals(ReminderAction.Cancel, reminderAction(task(null), now))
        assertEquals(ReminderAction.Cancel, reminderAction(null, now))
    }
}
