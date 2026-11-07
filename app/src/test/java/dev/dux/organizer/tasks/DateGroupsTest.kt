package dev.dux.organizer.tasks

import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DateGroupsTest {
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val today = LocalDate.of(2026, 11, 6)
    private fun task(title: String, due: LocalDate? = null, scheduled: LocalDate? = null, priority: Priority = Priority.NONE) =
        Task(title = title, dueDate = due, scheduledDate = scheduled, priority = priority, createdAt = now, updatedAt = now)

    @Test
    fun nearestFirstAndPriorityOnlyBreaksTies() {
        // Input order is the database's: priority first.
        val tasks = listOf(
            task("high, in 20 days", due = today.plusDays(20), priority = Priority.HIGH),
            task("high, no date", priority = Priority.HIGH),
            task("high, tomorrow", due = today.plusDays(1), priority = Priority.HIGH),
            task("in 5 days", due = today.plusDays(5)),
            task("in 3 days", scheduled = today.plusDays(3)),
            task("overdue", due = today.minusDays(2)),
            task("planned yesterday", scheduled = today.minusDays(1)),
            task("due today", due = today),
            task("tomorrow", due = today.plusDays(1)),
            task("no date"),
        )
        assertEquals(
            listOf(
                DateGroup.Overdue to listOf("overdue"),
                DateGroup.Today to listOf("planned yesterday", "due today"),
                DateGroup.Tomorrow to listOf("high, tomorrow", "tomorrow"),
                DateGroup.Week to listOf("in 3 days", "in 5 days"),
                DateGroup.Later to listOf("high, in 20 days"),
                DateGroup.NoDate to listOf("high, no date", "no date"),
            ),
            groupByDate(tasks, today).map { (g, ts) -> g to ts.map { it.title } },
        )
    }

    @Test
    fun theEarlierDateCountsAndEmptyGroupsAreLeftOut() {
        val planned = task("due later, planned tomorrow", due = today.plusDays(10), scheduled = today.plusDays(1))
        assertEquals(listOf(DateGroup.Tomorrow to listOf(planned)), groupByDate(listOf(planned), today))
        assertEquals(emptyList<Pair<DateGroup, List<Task>>>(), groupByDate(emptyList(), today))
    }
}
