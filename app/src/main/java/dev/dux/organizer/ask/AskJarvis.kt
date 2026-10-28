package dev.dux.organizer.ask

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.finances.MonthSummary
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Answers questions about the user's data with a small model that runs on the phone (Gemma 4 E2B via LiteRT-LM). */
class AskJarvis(private val context: Context) {
    // shortcut: the model is copied in by hand (adb) for now; a download from Ajustes comes next.
    val modelFile = File(context.filesDir, "jarvis.litertlm")
    private val lock = Mutex()
    private var engine: Engine? = null

    val installed: Boolean get() = modelFile.exists()

    /** Streams the answer. The first call loads the model (several seconds). */
    fun ask(question: String, facts: String): Flow<String> = flow {
        val engine = lock.withLock {
            engine ?: withContext(Dispatchers.Default) {
                Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), cacheDir = context.cacheDir.path)).also { it.initialize() }
            }.also { engine = it }
        }
        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "Eres Jarvis, el asistente de una app de organización personal. Responde en español, breve y claro, " +
                    "usando solo estos datos del usuario. Si no está en los datos, dilo.\n\n$facts",
            ),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
        )
        engine.createConversation(config).use { conversation ->
            conversation.sendMessageAsync(question).collect { emit(it.toString()) }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

/**
 * The user's data as plain text for the model. Overdue tasks and over-budget categories are worked out
 * here: in the spike the model missed some when it had to compare dates or amounts itself.
 */
fun askFacts(open: List<Task>, projectNames: Map<String, String>, month: MonthSummary?, today: LocalDate): String = buildString {
    fun line(t: Task) = buildString {
        append("- ").append(t.title)
        t.dueDate?.let { append(" (vence ").append(it.format(dayFormat)).append(")") }
        if (t.priority != Priority.NONE) append(", prioridad ").append(mapOf(Priority.LOW to "baja", Priority.MEDIUM to "media", Priority.HIGH to "alta")[t.priority])
        t.projectId?.let(projectNames::get)?.let { append(", proyecto ").append(it) }
    }
    appendLine("Hoy es ${today.format(dayFormat)} de ${today.year}.")
    val overdue = open.filter { it.dueDate != null && it.dueDate < today }
    val dueToday = open.filter { it.dueDate == today }
    val week = open.filter { it.dueDate != null && it.dueDate > today && it.dueDate <= today.plusDays(7) }
    val rest = open - (overdue + dueToday + week).toSet()
    for ((title, list) in listOf("Tareas vencidas" to overdue, "Tareas para hoy" to dueToday, "Tareas de los próximos 7 días" to week, "Otras tareas pendientes" to rest)) {
        appendLine("$title (${list.size}):")
        list.forEach { appendLine(line(it)) }
    }
    if (month != null) {
        appendLine("Finanzas de este mes: entró ${formatMoney(month.incomeCents)}, salió ${formatMoney(month.expenseCents)}, te queda ${formatMoney(month.balanceCents)}.")
        month.budgetCents?.let { appendLine("Presupuesto mensual: ${formatMoney(it)}.") }
        month.lines.filter { it.spentCents > 0 }.forEach { l ->
            append("- ${l.category.name}: gastado ${formatMoney(l.spentCents)}")
            l.category.monthlyBudgetCents?.let { append(" de ${formatMoney(it)}") }
            appendLine()
        }
        val over = month.lines.mapNotNull { l -> l.overCents?.let { "${l.category.name} (por ${formatMoney(it)})" } }
        appendLine("Categorías donde se pasó del presupuesto: ${over.joinToString().ifEmpty { "ninguna" }}.")
    }
}
