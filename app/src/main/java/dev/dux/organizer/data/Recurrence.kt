package dev.dux.organizer.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Frequency { DAILY, WEEKLY, MONTHLY }

/**
 * A repeat rule. Stored as an iCalendar RRULE subset (see docs/design/recurring-tasks.md),
 * plus `X-TIME`: the reminder time for every occurrence, since reminders are cleared when they fire.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    /** Weekly only; never empty for weekly rules. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Monthly only; 1–31, clamped to the month's length. */
    val monthDay: Int? = null,
    val reminderTime: LocalTime? = null,
) {
    init {
        require(interval in 1..MAX_INTERVAL) { "interval out of range" }
        require(frequency != Frequency.WEEKLY || weekdays.isNotEmpty()) { "weekly rule without weekdays" }
        require(frequency != Frequency.MONTHLY || monthDay in 1..31) { "monthly rule without a valid day" }
    }

    /**
     * First occurrence strictly after [after]. Intervals are counted from [anchor]'s day, week (starting
     * Monday) or month, so "every 2 weeks" keeps its rhythm.
     */
    fun nextAfter(anchor: LocalDate, after: LocalDate): LocalDate = when (frequency) {
        Frequency.DAILY -> {
            val periods = Math.floorDiv(ChronoUnit.DAYS.between(anchor, after), interval.toLong()) + 1
            anchor.plusDays(periods * interval)
        }
        Frequency.WEEKLY -> {
            val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            generateSequence(after.plusDays(1)) { it.plusDays(1) }.first { day ->
                val week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                day.dayOfWeek in weekdays && Math.floorMod(ChronoUnit.WEEKS.between(anchorWeek, week), interval.toLong()) == 0L
            }
        }
        Frequency.MONTHLY -> {
            val anchorMonth = anchor.withDayOfMonth(1)
            var months = Math.floorDiv(ChronoUnit.MONTHS.between(anchorMonth, after.withDayOfMonth(1)), interval.toLong()) * interval
            var candidate = dayIn(anchorMonth.plusMonths(months))
            while (candidate <= after) {
                months += interval
                candidate = dayIn(anchorMonth.plusMonths(months))
            }
            candidate
        }
    }

    /** First occurrence on or after [day], counting periods from [day] itself. */
    fun firstOnOrAfter(day: LocalDate): LocalDate = nextAfter(anchor = day, after = day.minusDays(1))

    private fun dayIn(month: LocalDate): LocalDate = month.withDayOfMonth(minOf(monthDay!!, month.lengthOfMonth()))

    fun format(): String = buildList {
        add("FREQ=$frequency")
        add("INTERVAL=$interval")
        if (frequency == Frequency.WEEKLY) add("BYDAY=" + weekdays.sorted().joinToString(",") { it.code })
        if (frequency == Frequency.MONTHLY) add("BYMONTHDAY=$monthDay")
        reminderTime?.let { add("X-TIME=%02d:%02d".format(it.hour, it.minute)) }
    }.joinToString(";")

    companion object {
        const val MAX_INTERVAL = 99

        /** Returns null if [text] is not a rule this app writes (a hand-edited backup, for example). */
        fun parse(text: String): Recurrence? = try {
            val parts = text.split(";").associate { part ->
                val (key, value) = part.split("=", limit = 2).also { require(it.size == 2) }
                key to value
            }
            Recurrence(
                frequency = Frequency.valueOf(parts.getValue("FREQ")),
                interval = parts["INTERVAL"]?.toInt() ?: 1,
                weekdays = parts["BYDAY"]?.split(",")?.map { code -> DayOfWeek.entries.first { it.code == code } }?.toSet().orEmpty(),
                monthDay = parts["BYMONTHDAY"]?.toInt(),
                reminderTime = parts["X-TIME"]?.let(LocalTime::parse),
            )
        } catch (e: Exception) {
            null
        }
    }
}

private val DayOfWeek.code: String get() = name.take(2)
