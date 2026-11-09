package dev.dux.organizer.inbox

import dev.dux.organizer.ask.relativeDate
import dev.dux.organizer.ask.reminderTime
import java.time.LocalDate
import java.time.LocalDateTime

/** A date and/or time found inside a title, and the title without those words. */
data class Spotted(val title: String, val due: LocalDate?, val remindAt: LocalDateTime?)

/**
 * "Llamar al dentista mañana a las 10" → ("Llamar al dentista", tomorrow, tomorrow 10:00). Reuses the phrases
 * Jarvis understands when asked by voice ([relativeDate], [reminderTime]), Spanish and English. Only the future
 * counts, a bare number is never a date ("Comprar 2 leches"), and a title left empty means nothing was found.
 * A [due] date already picked by hand wins: a time in the title is read on that day.
 */
fun spotWhen(text: String, now: LocalDateTime, due: LocalDate? = null): Spotted? {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size < 2) return null
    val today = now.toLocalDate()

    var date: LocalDate? = null
    var dateRange: IntRange? = null
    loop@ for (size in 4 downTo 1) for (start in 0..words.size - size) {
        val phrase = words.subList(start, start + size).joinToString(" ").trimEnd(',', '.')
        if (phrase.all { it.isDigit() }) continue
        val found = relativeDate(phrase, today)?.takeIf { !it.isBefore(today) } ?: continue
        date = found
        dateRange = start until start + size
        break@loop
    }

    var remindAt: LocalDateTime? = null
    var timeRange: IntRange? = null
    loop@ for (size in 5 downTo 1) for (start in 0..words.size - size) {
        val range = start until start + size
        if (dateRange != null && range.any { it in dateRange }) continue
        val phrase = words.subList(start, start + size).joinToString(" ").trimEnd(',', '.')
        if (!timeMarker.containsMatchIn(phrase) || cutsQualifier(words, start + size)) continue
        val found = reminderTime(phrase, due ?: date, now) ?: continue
        remindAt = found
        timeRange = range
        break@loop
    }
    if (date == null && remindAt == null) return null

    val kept = words.filterIndexed { i, _ -> dateRange?.contains(i) != true && timeRange?.contains(i) != true }.toMutableList()
    // "Pagar la luz para el viernes" leaves "para" hanging at the end.
    while (kept.isNotEmpty() && kept.last().lowercase() in connectors) kept.removeAt(kept.lastIndex)
    val title = kept.joinToString(" ").trimEnd(',', '.', ' ')
    if (title.isBlank()) return null
    return Spotted(title, date, remindAt)
}

// A plain number is not a time ("Comprar 5 kilos"): it needs "a las", "at", "al", "en", a colon or am/pm.
private val timeMarker = Regex("""^(a las|a la|at|al|en|in)\b|:|\b(am|pm|a\.m\.|p\.m\.)$|^(mediod[ií]a|noon)$""", RegexOption.IGNORE_CASE)
// "a las 8 am" already gone must not fall back to "a las 8" and leave "am" in the title.
private fun cutsQualifier(words: List<String>, next: Int): Boolean {
    val after = words.drop(next).take(2).joinToString(" ").lowercase()
    return after.split(" ").first() in setOf("am", "pm", "a.m.", "p.m.") || after in setOf("de la", "in the", "at night")
}
private val connectors = setOf("para", "el", "la", "de", "a", "y", "for", "on", "by", "at", "and")
