package dev.dux.organizer.ask

import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Frequency
import dev.dux.organizer.data.Recurrence
import java.time.LocalTime
import java.time.LocalDateTime
import dev.dux.organizer.data.parseMoney
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.Category
import java.util.UUID
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.ShoppingRepository
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Something Jarvis can do after the user confirms it. Built only from data that exists (see [parseAction]). */
sealed interface JarvisAction {
    data class AddShopping(val list: ShoppingList, val item: String, val quantity: String?) : JarvisAction
    data class CompleteTask(val task: Task) : JarvisAction
    /** [remindAt]: "recuérdame a las 5…" also sets the task's reminder. */
    data class CreateTask(val title: String, val due: LocalDate?, val remindAt: LocalDateTime? = null, val repeat: Recurrence? = null) : JarvisAction
    /** Only the non-null fields change. */
    data class EditTask(
        val task: Task,
        val newTitle: String? = null,
        val due: LocalDate? = null,
        val remindAt: LocalDateTime? = null,
        val priority: Priority? = null,
        val repeat: Recurrence? = null,
    ) : JarvisAction
    /** Expense or income: the category decides, as in the Finanzas screen. Dated today. */
    data class RecordMovement(val category: Category, val amountCents: Long, val note: String?, val date: LocalDate) : JarvisAction
}

/**
 * Reads the model's answer and checks it against the user's data: unknown tasks or lists give null,
 * so a made-up name never becomes an action. [command] is what the user said.
 */
fun parseAction(
    output: String,
    command: String,
    open: List<Task>,
    lists: List<ShoppingList>,
    today: LocalDate,
    categories: List<Category> = emptyList(),
    now: LocalDateTime = today.atStartOfDay(),
): JarvisAction? {
    val fields = jsonFields(output)
    return when (fields["accion"]) {
        "agregar_compra" -> {
            val item = fields["articulo"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // The model may echo how the facts name it ("lista de compras Súper"), so a name inside the text also counts.
            val list = fields["lista"]?.let { name ->
                lists.find { fold(it.name) == fold(name) } ?: lists.filter { fold(name).contains(fold(it.name)) }.maxByOrNull { it.name.length } ?: return null
            } ?: lists.firstOrNull() ?: return null
            // The model sometimes copies a quantity from the data ("Leche x2"); keep it only if the user said that number.
            val quantity = fields["cantidad"]?.trim()?.takeIf { q -> q.isNotEmpty() && q.filter(Char::isDigit).let { it.isNotEmpty() && it in command } }
            JarvisAction.AddShopping(list, item.replaceFirstChar { it.uppercase() }, quantity)
        }
        "completar_tarea" -> JarvisAction.CompleteTask(findTask(fields["tarea"] ?: return null, open) ?: return null)
        "crear_tarea" -> {
            val title = fields["titulo"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // The model sometimes puts the time inside "vence" ("hoy a las 5"): split it off.
            val (dayWords, timeWords) = splitTime(fields["vence"])
            val due = dayWords?.let { relativeDate(it, today) }
            // Or the whole time ("in 2 hours") lands in "vence": if it is not a date, try it as a time.
            val remindAt = (fields["hora"] ?: timeWords ?: dayWords.takeIf { due == null })?.let { reminderTime(it, due, now) }
            val repeat = fields["repite"]?.let { repeatRule(it, due ?: today) }
            // "Pay the rent every month" when that task already exists: change it rather than create a duplicate.
            open.find { fold(it.title) == fold(title) }?.let { existing ->
                return JarvisAction.EditTask(existing, due = due, remindAt = remindAt, repeat = repeat).takeIf { it != JarvisAction.EditTask(existing) }
            }
            JarvisAction.CreateTask(title.replaceFirstChar { it.uppercase() }, due ?: remindAt?.toLocalDate(), remindAt, repeat)
        }
        "editar_tarea" -> {
            val task = findTask(fields["tarea"] ?: return null, open) ?: return null
            val (dayWords, timeWords) = splitTime(fields["vence"])
            val due = dayWords?.let { relativeDate(it, today) }
            val remindAt = (fields["hora"] ?: timeWords ?: dayWords.takeIf { due == null })?.let { reminderTime(it, due ?: task.dueDate, now) }
            val change = JarvisAction.EditTask(
                task,
                newTitle = fields["titulo_nuevo"]?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() },
                due = due,
                remindAt = remindAt,
                priority = fields["prioridad"]?.let { priorities[fold(it)] },
                repeat = fields["repite"]?.let { repeatRule(it, due ?: today) },
            )
            // Nothing understood to change: no proposal rather than an empty one.
            change.takeIf { it != JarvisAction.EditTask(task) }
        }
        "registrar_movimiento" -> {
            val amount = fields["monto"]?.let(::parseMoney) ?: return null
            // A made-up amount is the costly mistake here: the user must have said those digits.
            if (Regex("""\d[\d,.]*""").findAll(command).none { parseMoney(it.value.trimEnd('.', ',')) == amount }) return null
            val wanted = fold(fields["categoria"] ?: return null)
            val category = categories.filter { !it.archived }.find { fold(it.name) == wanted } ?: return null
            // Money already moved: "el lunes" is last Monday; a date that is still to come is not accepted.
            val date = fields["fecha"]?.let { relativeDate(it, today, past = true) }?.takeIf { !it.isAfter(today) } ?: today
            JarvisAction.RecordMovement(category, amount, fields["nota"]?.trim()?.takeIf { it.isNotEmpty() }, date)
        }
        else -> null
    }
}

/**
 * Words the model copies from the command → a date; anything unknown → null (no date rather than a wrong one).
 * Spanish and English both work, whatever the app's language: "mañana", "en 3 días", "el 15", "la próxima
 * semana", "el viernes", "on the 15th", "in 2 weeks", "2026-11-02". [past]: for money already spent, so
 * "el lunes" and "el 15" mean the last ones, not the next ones.
 */
fun relativeDate(text: String, today: LocalDate, past: Boolean = false): LocalDate? {
    val words = fold(text).trim().removeSuffix(".")
    runCatching { return LocalDate.parse(words) }
    when (words) {
        "hoy", "today" -> return today
        "manana", "tomorrow" -> return today.plusDays(1)
        "pasado manana", "day after tomorrow", "the day after tomorrow" -> return today.plusDays(2)
        "ayer", "yesterday" -> return today.minusDays(1)
        "antier", "anteayer", "antes de ayer", "day before yesterday", "the day before yesterday" -> return today.minusDays(2)
        "la proxima semana", "proxima semana", "la semana que viene", "next week" -> return today.plusWeeks(1)
    }
    inAmount.matchEntire(words)?.let { m ->
        val n = m.groupValues[2].toLongOrNull() ?: 1 // "un", "una", "a", "an"
        return if (m.groupValues[3].startsWith("d")) today.plusDays(n) else today.plusWeeks(n)
    }
    dayOfMonth.matchEntire(words)?.let { m -> return nearestDayOfMonth(m.groupValues[1].toInt(), today, past) }
    val day = weekdays[words.removePrefix("el ").removePrefix("este ").removePrefix("on ").removePrefix("next ").removePrefix("this ")] ?: return null
    return today.with(if (past) TemporalAdjusters.previous(day) else TemporalAdjusters.next(day))
}

private val inAmount = Regex("""(en|in) (\d{1,3}|un|una|a|an) (dias?|days?|semanas?|weeks?)""")
private val dayOfMonth = Regex("""(?:el (?:dia )?|on (?:the )?|the )?(\d{1,2})(?:st|nd|rd|th)?""")

/** The next (or, for [past], the last) date with that day of the month; skips months that do not have it. */
private fun nearestDayOfMonth(day: Int, today: LocalDate, past: Boolean): LocalDate? {
    if (day !in 1..31) return null
    var month = YearMonth.from(today)
    repeat(12) {
        if (day <= month.lengthOfMonth()) {
            val date = month.atDay(day)
            if (if (past) !date.isAfter(today) else !date.isBefore(today)) return date
        }
        month = if (past) month.minusMonths(1) else month.plusMonths(1)
    }
    return null
}

/**
 * "a las 5", "17:30", "5 pm", "5:30 de la tarde", "en una hora", "in 30 minutes", "mediodía" → when to remind,
 * on [date] (today if null). An hour without morning/afternoon picks the next one still ahead ("a las 5" at
 * 9:00 is 17:00); with no date and the time already gone, tomorrow. Unknown or past → null.
 */
fun reminderTime(text: String, date: LocalDate?, now: LocalDateTime): LocalDateTime? {
    val words = fold(text).trim().removeSuffix(".")
    inDuration.matchEntire(words)?.let { m ->
        val n = m.groupValues[2].toLongOrNull() ?: 1 // "una", "un", "an", "a"
        val at = if (m.groupValues[3].startsWith("h")) now.plusHours(n) else now.plusMinutes(n)
        return at.withSecond(0).withNano(0)
    }
    val (hour, minute, half) = when (words.removePrefix("al ").removePrefix("at ")) {
        "mediodia", "noon" -> Triple(12, 0, "pm")
        else -> clockTime.matchEntire(words)?.let { m -> Triple(m.groupValues[1].toInt(), m.groupValues[2].ifEmpty { "0" }.toInt(), m.groupValues[3]) } ?: return null
    } ?: return null
    if (hour > 23 || minute > 59) return null
    val candidates = when {
        half.isEmpty() -> if (hour in 1..11) listOf(hour, hour + 12) else listOf(hour)
        half in morning -> listOf(hour % 12)
        else -> listOf(hour % 12 + 12)
    }
    val day = date ?: now.toLocalDate()
    candidates.map { day.atTime(it, minute) }.firstOrNull { it.isAfter(now) }?.let { return it }
    return if (date == null) day.plusDays(1).atTime(candidates.first(), minute) else null
}

/** "hoy a las 5" → ("hoy", "a las 5"); "a las 5" → (null, "a las 5"); "mañana" → ("mañana", null). */
private fun splitTime(text: String?): Pair<String?, String?> {
    val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null to null
    val i = Regex("""\b(a las|a la|at)\b""", RegexOption.IGNORE_CASE).find(t)?.range?.first ?: return t to null
    return t.substring(0, i).trim().ifEmpty { null } to t.substring(i).trim()
}

private val inDuration = Regex("""(en|in) (\d{1,3}|una|un|an|a) (horas?|hours?|minutos?|minutes?|mins?)""")
private val clockTime = Regex("""(?:a las |a la |at )?(\d{1,2})(?::(\d{2}))? ?(am|pm|a\.m\.|p\.m\.|de la manana|de la tarde|de la noche|in the morning|in the afternoon|in the evening|at night)?""")
private val morning = setOf("am", "a.m.", "de la manana", "in the morning")

/** An open task by exact title, or the only one containing the words. */
private fun findTask(name: String, open: List<Task>): Task? {
    val wanted = fold(name)
    return open.find { fold(it.title) == wanted } ?: open.singleOrNull { fold(it.title).contains(wanted) }
}

private val priorities = mapOf(
    "alta" to Priority.HIGH, "high" to Priority.HIGH, "media" to Priority.MEDIUM, "medium" to Priority.MEDIUM,
    "baja" to Priority.LOW, "low" to Priority.LOW, "ninguna" to Priority.NONE, "none" to Priority.NONE,
)

/**
 * "diario", "cada 2 días", "cada lunes y jueves", "cada semana", "cada mes el día 1", "every month on the 15th",
 * "every 2 weeks" → a repeat rule; weekly without days uses [anchor]'s weekday, monthly without a day its date
 * (the task's date when one was said, else today).
 */
fun repeatRule(text: String, anchor: LocalDate): Recurrence? {
    val today = anchor
    val words = fold(text)
    val interval = Regex("""(?:cada|every) (\d{1,2})\b""").find(words)?.groupValues?.get(1)?.toInt()?.coerceIn(1, Recurrence.MAX_INTERVAL) ?: 1
    val days = weekdays.filterKeys { Regex("""\b$it""").containsMatchIn(words) }.values.toSet()
    val monthDay = Regex("""(?:dia|el|on the|on|the) (\d{1,2})(?:st|nd|rd|th)?\b""").find(words)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }
    return when {
        Regex("""\b(mes|meses|mensual|month|months|monthly)\b""").containsMatchIn(words) ->
            Recurrence(Frequency.MONTHLY, interval, monthDay = monthDay ?: today.dayOfMonth)
        days.isNotEmpty() -> Recurrence(Frequency.WEEKLY, interval, weekdays = days)
        Regex("""\b(semana|semanas|semanal|week|weeks|weekly)\b""").containsMatchIn(words) ->
            Recurrence(Frequency.WEEKLY, interval, weekdays = setOf(today.dayOfWeek))
        Regex("""\b(dia|dias|diario|diariamente|day|days|daily)\b""").containsMatchIn(words) -> Recurrence(Frequency.DAILY, interval)
        else -> null
    }
}

private val weekdays = mapOf(
    "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY,
    "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
    "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
    "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
)

/** Lowercase without accents, so "Súper" matches "super". */
private fun fold(text: String) = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

// shortcut: reads flat {"key":"value"|null} objects only, which is all the prompt asks for; use a JSON parser if actions get nested fields.
private fun jsonFields(text: String): Map<String, String> =
    Regex(""""(\w+)"\s*:\s*"([^"]*)"""").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

/** Does the action and returns how to undo it; null if nothing was changed. */
suspend fun JarvisAction.run(tasks: TaskRepository, shopping: ShoppingRepository, finance: FinanceRepository, today: LocalDate): (suspend () -> Unit)? {
    val id = UUID.randomUUID().toString()
    return when (this) {
        is JarvisAction.AddShopping -> if (shopping.addItem(list.id, item, quantity.orEmpty(), id)) ({ shopping.deleteItem(id) }) else null
        is JarvisAction.CompleteTask -> tasks.complete(task.id)
        is JarvisAction.CreateTask -> if (tasks.capture(title, dueDate = due, id = id)) {
            remindAt?.let { tasks.setReminder(id, it) }
            repeat?.let { tasks.setRecurrence(id, it) }
            // Deleting also cancels the alarm.
            ({ tasks.delete(id) })
        } else null
        is JarvisAction.EditTask -> {
            newTitle?.let { tasks.rename(task.id, it) }
            due?.let { tasks.setDueDate(task.id, it) }
            remindAt?.let { tasks.setReminder(task.id, it) }
            priority?.let { tasks.setPriority(task.id, it) }
            repeat?.let { tasks.setRecurrence(task.id, it) }
            val before = task
            // Undo puts back each field that could have changed (repeating can also set the scheduled date).
            ({
                tasks.rename(before.id, before.title)
                tasks.setDueDate(before.id, before.dueDate)
                tasks.setScheduledDate(before.id, before.scheduledDate)
                tasks.setReminder(before.id, before.reminderAt)
                tasks.setPriority(before.id, before.priority)
                tasks.setRecurrence(before.id, before.recurrence)
            })
        }
        is JarvisAction.RecordMovement ->
            if (finance.addMovement(amountCents, category.id, date, note.orEmpty(), id)) ({ finance.deleteMovement(id) }) else null
    }
}
