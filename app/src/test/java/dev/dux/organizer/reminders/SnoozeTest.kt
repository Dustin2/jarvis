package dev.dux.organizer.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SnoozeTest {
    @Test fun aWeekdayMorningOffersEverything() {
        // Wednesday 2026-11-04, 10:20:45.
        val options = snoozeOptions(LocalDateTime.of(2026, 11, 4, 10, 20, 45)).toMap()
        assertEquals(LocalDateTime.of(2026, 11, 4, 11, 20), options[Snooze.InAnHour])
        assertEquals(LocalDateTime.of(2026, 11, 4, 18, 0), options[Snooze.ThisEvening])
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), options[Snooze.Tomorrow])
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), options[Snooze.NextMonday])
    }

    @Test fun lateInTheDayThereIsNoEvening() {
        val options = snoozeOptions(LocalDateTime.of(2026, 11, 4, 17, 30)).map { it.first }
        assertEquals(listOf(Snooze.InAnHour, Snooze.Tomorrow, Snooze.NextMonday), options)
    }

    @Test fun onSundayMondayIsJustTomorrow() {
        // Sunday 2026-11-08.
        val options = snoozeOptions(LocalDateTime.of(2026, 11, 8, 20, 0)).map { it.first }
        assertEquals(listOf(Snooze.InAnHour, Snooze.Tomorrow), options)
    }
}
