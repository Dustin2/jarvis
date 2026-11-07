package dev.dux.organizer.reminders

import androidx.annotation.StringRes
import dev.dux.organizer.R
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One-tap choices for "Posponer", so snoozing rarely needs the date and time pickers. */
enum class Snooze(@StringRes val label: Int) {
    InAnHour(R.string.snooze_in_an_hour),
    ThisEvening(R.string.snooze_this_evening),
    Tomorrow(R.string.snooze_tomorrow),
    NextMonday(R.string.snooze_next_monday),
}

/** The choices that make sense at [now], each with its moment. "Esta tarde" only before 17:00; "El lunes" not when it is tomorrow. */
fun snoozeOptions(now: LocalDateTime): List<Pair<Snooze, LocalDateTime>> {
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    return buildList {
        add(Snooze.InAnHour to now.plusHours(1).truncatedTo(ChronoUnit.MINUTES))
        if (now.toLocalTime() < LocalTime.of(17, 0)) add(Snooze.ThisEvening to today.atTime(EVENING))
        add(Snooze.Tomorrow to tomorrow.atTime(MORNING))
        if (monday != tomorrow) add(Snooze.NextMonday to monday.atTime(MORNING))
    }
}

private val MORNING = LocalTime.of(9, 0)
private val EVENING = LocalTime.of(18, 0)
