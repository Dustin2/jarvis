package dev.dux.organizer.ask

import java.util.UUID
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.ShoppingRepository
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskRepository
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Something Jarvis can do after the user confirms it. Built only from data that exists (see [parseAction]). */
sealed interface JarvisAction {
    data class AddShopping(val list: ShoppingList, val item: String, val quantity: String?) : JarvisAction
    data class CompleteTask(val task: Task) : JarvisAction
    data class CreateTask(val title: String, val due: LocalDate?) : JarvisAction
}

/** Prompt for turning a command into one action. Dates come back as words; [relativeDate] turns them into days. */
const val ACTION_INSTRUCTION = """Eres Jarvis. Convierte la orden del usuario en UNA acción JSON, sin texto extra. Acciones posibles:
{"accion":"agregar_compra","articulo":"...","cantidad":"... o null","lista":"nombre exacto de la lista o null"}
{"accion":"completar_tarea","tarea":"título exacto de la tarea"}
{"accion":"crear_tarea","titulo":"...","vence":"hoy, mañana, pasado mañana, un día de la semana, o null"}
{"accion":"ninguna"}  (si es una pregunta o no es ninguna de las anteriores)
Algo que hay que hacer (lavar, llamar, pagar) es una tarea, no una compra. Usa solo nombres de tareas y listas que aparecen en los datos."""

/**
 * Reads the model's answer and checks it against the user's data: unknown tasks or lists give null,
 * so a made-up name never becomes an action. [command] is what the user said.
 */
fun parseAction(output: String, command: String, open: List<Task>, lists: List<ShoppingList>, today: LocalDate): JarvisAction? {
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
        else -> null
    }
}

/** "mañana", "el viernes", "2026-11-02" → a date; anything else → null (no due date rather than a wrong one). */
fun relativeDate(text: String, today: LocalDate): LocalDate? {
    val word = fold(text).removePrefix("el ").trim()
    runCatching { return LocalDate.parse(word) }
    return when (word) {
        "hoy" -> today
        "manana" -> today.plusDays(1)
        "pasado manana" -> today.plusDays(2)
        else -> weekdays[word]?.let { today.with(TemporalAdjusters.next(it)) }
    }
}

private val weekdays = mapOf(
    "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY,
    "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
)

/** Lowercase without accents, so "Súper" matches "super". */
private fun fold(text: String) = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

// shortcut: reads flat {"key":"value"|null} objects only, which is all the prompt asks for; use a JSON parser if actions get nested fields.
private fun jsonFields(text: String): Map<String, String> =
    Regex(""""(\w+)"\s*:\s*"([^"]*)"""").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

/** Does the action and returns how to undo it; null if nothing was changed. */
suspend fun JarvisAction.run(tasks: TaskRepository, shopping: ShoppingRepository): (suspend () -> Unit)? {
    val id = UUID.randomUUID().toString()
    return when (this) {
        is JarvisAction.AddShopping -> if (shopping.addItem(list.id, item, quantity.orEmpty(), id)) ({ shopping.deleteItem(id) }) else null
        is JarvisAction.CompleteTask -> tasks.complete(task.id)
        is JarvisAction.CreateTask -> if (tasks.capture(title, dueDate = due, id = id)) ({ tasks.delete(id) }) else null
    }
}
