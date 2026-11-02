package dev.dux.organizer.reminders

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningBriefTest {
    private val eight = LocalTime.of(8, 0)

    @Test
    fun todayIfStillAheadOtherwiseTomorrow() {
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 0), nextBriefAt(LocalDateTime.of(2026, 11, 2, 7, 30), eight))
        assertEquals(LocalDateTime.of(2026, 11, 3, 8, 0), nextBriefAt(LocalDateTime.of(2026, 11, 2, 8, 0), eight)) // just fired
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), nextBriefAt(LocalDateTime.of(2026, 11, 30, 21, 0), eight))
    }
}
