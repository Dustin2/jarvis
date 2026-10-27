package dev.dux.organizer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceTest {
    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test fun dailyEveryN() {
        val rule = Recurrence(Frequency.DAILY, interval = 3)
        assertEquals(d(10, 30), rule.nextAfter(anchor = d(10, 27), after = d(10, 27)))
        // Completed late: keeps the 3-day rhythm from the anchor and skips missed occurrences.
        assertEquals(d(11, 2), rule.nextAfter(anchor = d(10, 27), after = d(10, 31)))
    }

    @Test fun weeklyOnSeveralDays() {
        val rule = Recurrence(Frequency.WEEKLY, weekdays = setOf(MONDAY, WEDNESDAY, FRIDAY))
        // 2026-10-26 is a Monday.
        assertEquals(d(10, 28), rule.nextAfter(d(10, 26), d(10, 26)))
        assertEquals(d(11, 2), rule.nextAfter(d(10, 30), d(10, 30)))
    }

    @Test fun weeklyEveryTwoWeeksKeepsItsRhythm() {
        val rule = Recurrence(Frequency.WEEKLY, interval = 2, weekdays = setOf(MONDAY))
        assertEquals(d(11, 9), rule.nextAfter(anchor = d(10, 26), after = d(10, 26)))
        // Even when completed in the "off" week, the next one stays on the anchor's rhythm.
        assertEquals(d(11, 9), rule.nextAfter(anchor = d(10, 26), after = d(11, 3)))
    }

    @Test fun monthlyClampsToTheLastDay() {
        val rule = Recurrence(Frequency.MONTHLY, monthDay = 31)
        assertEquals(d(2, 28, 2027), rule.nextAfter(d(1, 31, 2027), d(1, 31, 2027)))
        assertEquals(d(2, 29, 2028), rule.nextAfter(d(1, 31, 2028), d(1, 31, 2028)))
        assertEquals(d(3, 31, 2027), rule.nextAfter(d(2, 28, 2027), d(2, 28, 2027)))
    }

    @Test fun monthlyLateAndEarlyCompletion() {
        val rule = Recurrence(Frequency.MONTHLY, monthDay = 10)
        // Bill due on the 10th, paid on the 15th: next is the 10th of next month.
        assertEquals(d(11, 10), rule.nextAfter(anchor = d(10, 10), after = d(10, 15)))
        // Paid early on the 5th: next is still next month's 10th, not the same period again.
        assertEquals(d(11, 10), rule.nextAfter(anchor = d(10, 10), after = d(10, 10)))
    }

    @Test fun yearlyIsEveryTwelveMonths() {
        val rule = Recurrence(Frequency.MONTHLY, interval = 12, monthDay = 15)
        assertEquals(d(3, 15, 2027), rule.nextAfter(d(3, 15), d(3, 15)))
    }

    @Test fun firstOnOrAfter() {
        assertEquals(d(10, 27), Recurrence(Frequency.DAILY).firstOnOrAfter(d(10, 27)))
        assertEquals(d(11, 2), Recurrence(Frequency.WEEKLY, weekdays = setOf(MONDAY)).firstOnOrAfter(d(10, 27)))
        assertEquals(d(11, 10), Recurrence(Frequency.MONTHLY, monthDay = 10).firstOnOrAfter(d(10, 27)))
        assertEquals(d(10, 30), Recurrence(Frequency.MONTHLY, monthDay = 30).firstOnOrAfter(d(10, 27)))
    }

    @Test fun formatAndParseRoundTrip() {
        val rules = listOf(
            Recurrence(Frequency.DAILY, interval = 3),
            Recurrence(Frequency.WEEKLY, weekdays = setOf(FRIDAY, MONDAY), reminderTime = LocalTime.of(9, 5)),
            Recurrence(Frequency.MONTHLY, interval = 12, monthDay = 10),
        )
        rules.forEach { assertEquals(it, Recurrence.parse(it.format())) }
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,FR;X-TIME=09:05", rules[1].format())
    }

    @Test fun invalidTextIsRejected() {
        listOf("", "FREQ=YEARLY", "FREQ=WEEKLY;INTERVAL=1", "FREQ=MONTHLY;BYMONTHDAY=32", "FREQ=DAILY;INTERVAL=0", "basura")
            .forEach { assertNull(it, Recurrence.parse(it)) }
    }
}
