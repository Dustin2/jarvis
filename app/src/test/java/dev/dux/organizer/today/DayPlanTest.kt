package dev.dux.organizer.today

import dev.dux.organizer.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DayPlanTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val now = Instant.parse("2026-10-26T10:00:00Z")

    private fun task(title: String, due: LocalDate? = null, scheduled: LocalDate? = null) =
        Task(title = title, dueDate = due, scheduledDate = scheduled, createdAt = now, updatedAt = now)

    @Test fun groupsByDate() {
        val plan = planDay(
            listOf(
                task("vencida", due = today.minusDays(1)),
                task("vence hoy", due = today),
                task("programada hoy", scheduled = today),
                task("programada ayer", scheduled = today.minusDays(1)),
                task("en 3 días", due = today.plusDays(3)),
                task("en 7 días", scheduled = today.plusDays(7)),
                task("en 8 días", due = today.plusDays(8)),
                task("sin fecha"),
            ),
            today,
        )
        assertEquals(listOf("vencida"), plan.overdue.map { it.title })
        assertEquals(listOf("vence hoy", "programada hoy", "programada ayer"), plan.today.map { it.title })
        assertEquals(listOf("en 3 días", "en 7 días"), plan.upcoming.map { it.title })
        assertEquals(8, plan.openCount)
    }

    @Test fun overdueWinsOverScheduledToday() {
        val plan = planDay(listOf(task("x", due = today.minusDays(2), scheduled = today)), today)
        assertEquals(1, plan.overdue.size)
        assertEquals(0, plan.today.size)
    }

    @Test fun upcomingIsSortedByNearestDate() {
        val plan = planDay(
            listOf(task("lejos", due = today.plusDays(5)), task("cerca", scheduled = today.plusDays(1))),
            today,
        )
        assertEquals(listOf("cerca", "lejos"), plan.upcoming.map { it.title })
    }

    @Test fun nextStepPrefersOverdueThenToday() {
        assertEquals("v", planDay(listOf(task("h", due = today), task("v", due = today.minusDays(1))), today).nextStep?.title)
        assertEquals("h", planDay(listOf(task("h", due = today)), today).nextStep?.title)
        assertNull(planDay(listOf(task("p", due = today.plusDays(2))), today).nextStep)
    }
}
