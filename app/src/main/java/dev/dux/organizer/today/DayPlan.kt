package dev.dux.organizer.today

import dev.dux.organizer.data.Task
import java.time.LocalDate

data class DayPlan(
    val overdue: List<Task>,
    val today: List<Task>,
    val upcoming: List<Task>,
    val openCount: Int,
) {
    /** Overdue goes first: it should already be done. Within each group the input order is kept. */
    val nextStep: Task? get() = overdue.firstOrNull() ?: today.firstOrNull()
}

const val UPCOMING_DAYS = 7L

/**
 * "Mi día" rules over open tasks (already sorted by priority):
 * - Overdue: its due date has passed.
 * - Today: due today, or scheduled for today or an earlier day (planned and not done carries over; it does not become "overdue").
 * - Upcoming: due or scheduled within the next [UPCOMING_DAYS] days.
 * Each task appears in a single group, the first one that applies.
 */
fun planDay(open: List<Task>, today: LocalDate): DayPlan {
    val horizon = today.plusDays(UPCOMING_DAYS)
    val overdue = mutableListOf<Task>()
    val forToday = mutableListOf<Task>()
    val upcoming = mutableListOf<Task>()
    for (task in open) {
        val due = task.dueDate
        val scheduled = task.scheduledDate
        when {
            due != null && due < today -> overdue += task
            due == today || (scheduled != null && scheduled <= today) -> forToday += task
            listOfNotNull(due, scheduled).any { it <= horizon } -> upcoming += task
        }
    }
    upcoming.sortBy { listOfNotNull(it.dueDate, it.scheduledDate).min() }
    return DayPlan(overdue, forToday, upcoming, open.size)
}
