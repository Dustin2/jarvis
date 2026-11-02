package dev.dux.organizer.ask

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
    data class CreateTask(val title: String, val due: LocalDate?) : JarvisAction
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
        "completar_tarea" -> {
            val wanted = fold(fields["tarea"] ?: return null)
            val task = open.find { fold(it.title) == wanted } ?: open.singleOrNull { fold(it.title).contains(wanted) } ?: return null
            JarvisAction.CompleteTask(task)
        }
        "crear_tarea" -> {
            val title = fields["titulo"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            JarvisAction.CreateTask(title.replaceFirstChar { it.uppercase() }, fields["vence"]?.let { relativeDate(it, today) })
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
        is JarvisAction.CreateTask -> if (tasks.capture(title, dueDate = due, id = id)) ({ tasks.delete(id) }) else null
        is JarvisAction.RecordMovement ->
            if (finance.addMovement(amountCents, category.id, date, note.orEmpty(), id)) ({ finance.deleteMovement(id) }) else null
    }
}
