package dev.dux.organizer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TaskRulesTest {
    private val t0 = Instant.parse("2026-10-25T10:00:00Z")
    private val t1 = Instant.parse("2026-10-25T11:00:00Z")
    private val task = Task(title = "x", createdAt = t0, updatedAt = t0)

    @Test fun completingSetsCompletedAt() {
        val done = task.withStatus(TaskStatus.DONE, t1)
        assertEquals(t1, done.completedAt)
        assertEquals(t1, done.updatedAt)
    }

    @Test fun completingTwiceKeepsOriginalCompletedAt() {
        val done = task.withStatus(TaskStatus.DONE, t0).withStatus(TaskStatus.DONE, t1)
        assertEquals(t0, done.completedAt)
    }

    @Test fun reopeningOrCancellingClearsCompletedAt() {
        val done = task.withStatus(TaskStatus.DONE, t0)
        assertNull(done.withStatus(TaskStatus.TODO, t1).completedAt)
        assertNull(done.withStatus(TaskStatus.CANCELLED, t1).completedAt)
    }

    @Test fun titleIsTrimmedAndValidated() {
        assertEquals("Comprar hielo", cleanTitle("  Comprar hielo \n"))
        assertNull(cleanTitle("   "))
        assertNull(cleanTitle("a".repeat(MAX_TITLE_LENGTH + 1)))
        assertEquals(MAX_TITLE_LENGTH, cleanTitle("a".repeat(MAX_TITLE_LENGTH))?.length)
    }
}
