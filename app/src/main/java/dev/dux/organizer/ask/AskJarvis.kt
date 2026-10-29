package dev.dux.organizer.ask

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.finances.MonthSummary
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Pinned revision: a new upload on Hugging Face cannot change what Jarvis downloads.
private const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it-gpu.litertlm"
const val MODEL_BYTES = 2_008_432_640L

private val Context.askStore by preferencesDataStore("ask_jarvis")
private val DOWNLOAD_ID = longPreferencesKey("download_id")
private val FAILED = booleanPreferencesKey("failed")

sealed interface ModelState {
    data object NotInstalled : ModelState
    /** [progress] 0..1, null while the size is unknown. [waitingForWifi]: queued until there is Wi-Fi. */
    data class Downloading(val progress: Float?, val waitingForWifi: Boolean) : ModelState
    data object Installed : ModelState
    data object Failed : ModelState
}

/** Answers questions about the user's data with a small model that runs on the phone (Gemma 4 E2B via LiteRT-LM). */
class AskJarvis(private val context: Context) {
    // External files dir: DownloadManager can write there without permissions, and it is removed with the app.
    private val dir get() = context.getExternalFilesDir(null) ?: context.filesDir
    val modelFile get() = File(dir, "jarvis.litertlm")
    private val partFile get() = File(dir, "jarvis.litertlm.part")
    private val downloads get() = context.getSystemService(DownloadManager::class.java)
    private val lock = Mutex()
    private var engine: Engine? = null

    val installed: Boolean get() = modelFile.exists()

    /** Polls DownloadManager while collected; finishes the install when the download completes. */
    val state: Flow<ModelState> = flow {
        while (true) {
            emit(check())
            delay(1_000)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    private suspend fun check(): ModelState {
        if (installed) return ModelState.Installed
        val prefs = context.askStore.data.first()
        val id = prefs[DOWNLOAD_ID] ?: return if (prefs[FAILED] == true) ModelState.Failed else ModelState.NotInstalled
        downloads.query(DownloadManager.Query().setFilterById(id)).use { c ->
            // Gone: the user cancelled it from the notification.
            if (!c.moveToFirst()) return finish(failed = false)
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    // shortcut: checks the size, not the sha256; hash it if the model ever comes from a less trusted host.
                    if (partFile.length() == MODEL_BYTES && partFile.renameTo(modelFile)) {
                        finish(failed = false)
                        ModelState.Installed
                    } else finish(failed = true)
                DownloadManager.STATUS_FAILED -> finish(failed = true)
                else -> ModelState.Downloading(
                    progress = (done.toFloat() / MODEL_BYTES).coerceIn(0f, 1f).takeIf { done > 0 },
                    waitingForWifi = status == DownloadManager.STATUS_PAUSED && reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI,
                )
            }
        }
    }

    private suspend fun finish(failed: Boolean): ModelState {
        partFile.delete()
        context.askStore.edit { it.remove(DOWNLOAD_ID); it[FAILED] = failed }
        return if (failed) ModelState.Failed else ModelState.NotInstalled
    }

    /** Wi-Fi only: 2 GB over mobile data could eat a whole plan. */
    suspend fun download() {
        partFile.delete()
        val request = DownloadManager.Request(MODEL_URL.toUri())
            .setTitle("Modelo de Jarvis")
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(partFile))
        val id = downloads.enqueue(request)
        context.askStore.edit { it[DOWNLOAD_ID] = id; it[FAILED] = false }
    }

    /** Cancels a download in progress or removes the installed model. */
    suspend fun delete() {
        context.askStore.data.first()[DOWNLOAD_ID]?.let { downloads.remove(it) }
        lock.withLock {
            engine?.close()
            engine = null
        }
        withContext(Dispatchers.IO) { modelFile.delete() }
        finish(failed = false)
    }

    /** Streams the answer. The first call loads the model (several seconds). */
    fun ask(question: String, facts: String): Flow<String> = generate(
        "Eres Jarvis, el asistente de una app de organización personal. Responde en español, breve y claro, " +
            "usando solo estos datos del usuario. Si no está en los datos, dilo. Tú no puedes hacer cambios: si te piden " +
            "agregar, crear o completar algo, nunca digas que ya lo hiciste; di que no encontraste esa tarea o lista y que lo pidan de otra forma.",
        facts,
        question,
    )

    /** The model's raw action JSON for [command]; see [parseAction]. */
    suspend fun proposeAction(command: String, facts: String): String = generate(ACTION_INSTRUCTION, facts, command).toList().joinToString("")

    private fun generate(instruction: String, facts: String, prompt: String): Flow<String> = flow {
        val engine = lock.withLock {
            engine ?: withContext(Dispatchers.Default) {
                Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), cacheDir = context.cacheDir.path)).also { it.initialize() }
            }.also { engine = it }
        }
        val config = ConversationConfig(
            systemInstruction = Contents.of("$instruction\n\n$facts"),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
        )
        engine.createConversation(config).use { conversation ->
            conversation.sendMessageAsync(prompt).collect { emit(it.toString()) }
        }
    }
}

/** What Jarvis knows when asked: the facts text for the model, and the real records to check actions against. */
data class AskData(
    val open: List<Task>,
    val projectNames: Map<String, String>,
    val month: MonthSummary?,
    val today: LocalDate,
    val lists: List<ShoppingList>,
    val items: List<ShoppingItem>,
) {
    fun facts() = askFacts(open, projectNames, month, today, lists, items)
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

/**
 * The user's data as plain text for the model. Overdue tasks and over-budget categories are worked out
 * here: in the spike the model missed some when it had to compare dates or amounts itself.
 */
fun askFacts(
    open: List<Task>,
    projectNames: Map<String, String>,
    month: MonthSummary?,
    today: LocalDate,
    lists: List<ShoppingList> = emptyList(),
    items: List<ShoppingItem> = emptyList(),
): String = buildString {
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
    val byList = items.groupBy { it.listId }
    for (list in lists) {
        fun names(checked: Boolean) = byList[list.id].orEmpty().filter { it.checked == checked }
            .joinToString { i -> i.name + (i.quantity?.let { " ($it)" } ?: "") }.ifEmpty { "nada" }
        appendLine("Lista de compras ${list.name}: por comprar: ${names(false)}. Ya en el carrito: ${names(true)}.")
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
