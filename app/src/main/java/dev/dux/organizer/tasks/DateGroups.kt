package dev.dux.organizer.tasks

import androidx.annotation.StringRes
import dev.dux.organizer.R
import dev.dux.organizer.data.Task
import java.time.LocalDate

/** The sections of "Por hacer", nearest first. */
enum class DateGroup(@StringRes val label: Int) {
    Overdue(R.string.overdue),
    Today(R.string.today),
    Tomorrow(R.string.tomorrow),
    Week(R.string.upcoming),
    Later(R.string.later),
    NoDate(R.string.no_date),
}

/**
 * Groups open tasks by how soon they are, with the same rules as Mi día: overdue only by due date, and a
 * scheduled day that passed carries over to today. A task's day is its earlier date (due or scheduled).
 * Within a group: by day, then the input order (priority), so priority only breaks ties.
 */
fun groupByDate(tasks: List<Task>, today: LocalDate): List<Pair<DateGroup, List<Task>>> {
    fun day(t: Task) = listOfNotNull(t.dueDate, t.scheduledDate).minOrNull()
    fun group(t: Task): DateGroup {
        val day = day(t) ?: return DateGroup.NoDate
        return when {
            t.dueDate != null && t.dueDate < today -> DateGroup.Overdue
            day <= today -> DateGroup.Today
            day == today.plusDays(1) -> DateGroup.Tomorrow
            day <= today.plusDays(7) -> DateGroup.Week
            else -> DateGroup.Later
        }
    }
    val byGroup = tasks.groupBy(::group)
    return DateGroup.entries.mapNotNull { g -> byGroup[g]?.sortedBy { day(it) ?: LocalDate.MAX }?.let { g to it } }
}
