package dev.dux.organizer.ask

import java.time.temporal.TemporalAdjusters
import java.time.YearMonth
import java.time.DayOfWeek
import dev.dux.organizer.data.Movement
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicInteger
import com.google.ai.edge.litertlm.Message
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.R
import dev.dux.organizer.data.Category
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
    /** Answers in progress: the model is never released under one. */
    private val active = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * No screen of the app is visible: free the model's ~2 GB right away. Not after a delay: Android freezes
     * background apps within seconds, so a timer would only run once the app is opened again. Coming back,
     * the sheet's warm-up loads it while the user types.
     */
    fun onAppHidden() {
        if (engine == null) return
        scope.launch {
            lock.withLock {
                if (active.get() == 0) {
                    engine?.close()
                    engine = null
                }
            }
        }
    }

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
            .setTitle(context.getString(R.string.model_download_title))
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
    fun ask(question: String, facts: String, history: List<Turn> = emptyList()): Flow<String> =
        generate(context.getString(R.string.ask_prompt_answer), facts, question, history)

    /** The model's raw action JSON for [command]; see [parseAction]. */
    suspend fun proposeAction(command: String, facts: String, history: List<Turn> = emptyList()): String =
        generate(context.getString(R.string.ask_prompt_action), facts, command, history).toList().joinToString("")

    /** Loads the model (~10 s on an S25) if it is not loaded yet; kept until the process dies or the model is deleted. */
    private suspend fun engine(): Engine = lock.withLock {
        engine ?: withContext(Dispatchers.Default) {
            Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), cacheDir = context.cacheDir.path)).also { it.initialize() }
        }.also { engine = it }
    }

    /** Called when the sheet opens: the model loads while the user types or speaks, not after they send. */
    suspend fun warmUp() {
        if (installed) engine()
    }

    /** [history]: earlier turns of this conversation, so "marca la primera" knows what "la primera" is. */
    private fun generate(instruction: String, facts: String, prompt: String, history: List<Turn>): Flow<String> = flow {
        active.incrementAndGet()
        try {
            emitAll(answer(engine(), instruction, facts, prompt, history))
        } finally {
            active.decrementAndGet()
        }
    }

    private fun answer(engine: Engine, instruction: String, facts: String, prompt: String, history: List<Turn>): Flow<String> = flow {
        val config = ConversationConfig(
            systemInstruction = Contents.of("$instruction\n\n$facts"),
            initialMessages = history.flatMap { listOf(Message.user(it.question), Message.model(it.answer)) },
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
        )
        engine.createConversation(config).use { conversation ->
            conversation.sendMessageAsync(prompt).collect { emit(it.toString()) }
        }
    }
}

/** One question and what Jarvis answered, kept while the sheet is open. */
data class Turn(val question: String, val answer: String)

/** What Jarvis knows when asked: the facts text for the model, and the real records to check actions against. */
data class AskData(
    val open: List<Task>,
    val projectNames: Map<String, String>,
    val month: MonthSummary?,
    val today: LocalDate,
    val lists: List<ShoppingList>,
    val items: List<ShoppingItem>,
    val categories: List<Category> = emptyList(),
    val labels: FactLabels = FactLabels.SPANISH,
    /** This month's and last month's, for "¿cuánto gasté esta semana?" and "¿y el mes pasado?". */
    val movements: List<Movement> = emptyList(),
) {
    fun facts() = askFacts(open, projectNames, month, today, lists, items, categories, labels, movements)
}

/**
 * Words for the facts text, in the app's language. Kotlin rather than string resources: it is read by
 * the model, not shown, and the facts must stay testable on the JVM.
 */
class FactLabels(
    val locale: Locale,
    val dayPattern: String,
    val today: String,
    val due: String,
    val priority: String,
    val priorities: Map<Priority, String>,
    val project: String,
    val overdue: String,
    val dueToday: String,
    val week: String,
    val others: String,
    val nothing: String,
    val list: String,
    val inCart: String,
    val finances: String,
    val budget: String,
    val spent: String,
    val of: String,
    val overBudget: String,
    val by: String,
    val none: String,
    val expenseCategories: String,
    val incomeCategories: String,
    val thisWeek: String,
    val lastMonth: String,
) {
    companion object {
        val SPANISH = FactLabels(
            Locale.forLanguageTag("es-MX"), "EEEE d 'de' MMMM 'de' yyyy", "Hoy es %s.", "vence", "prioridad",
            mapOf(Priority.LOW to "baja", Priority.MEDIUM to "media", Priority.HIGH to "alta"), "proyecto",
            "Tareas vencidas", "Tareas para hoy", "Tareas de los próximos 7 días", "Otras tareas pendientes", "nada",
            "Lista de compras %s: por comprar: %s.", "Ya en el carrito: %s.",
            "Finanzas de este mes: entró %s, salió %s, te queda %s.", "Presupuesto mensual: %s.", "gastado", "de",
            "Categorías donde se pasó del presupuesto: %s.", "por", "ninguna", "Categorías de gasto", "Categorías de ingreso",
            "Gastos de esta semana (desde el %s): %s. Total: %s.", "Mes pasado (%s): entró %s, salió %s. Gastos por categoría: %s.",
        )
        val ENGLISH = FactLabels(
            Locale.ENGLISH, "EEEE, MMMM d, yyyy", "Today is %s.", "due", "priority",
            mapOf(Priority.LOW to "low", Priority.MEDIUM to "medium", Priority.HIGH to "high"), "project",
            "Overdue tasks", "Tasks due today", "Tasks due in the next 7 days", "Other open tasks", "nothing",
            "Shopping list %s: to buy: %s.", "Already in the cart: %s.",
            "This month's money: came in %s, went out %s, left %s.", "Monthly budget: %s.", "spent", "of",
            "Categories over budget: %s.", "by", "none", "Expense categories", "Income categories",
            "Spending this week (since %s): %s. Total: %s.", "Last month (%s): came in %s, went out %s. Spending by category: %s.",
        )

        fun of(locale: Locale) = if (locale.language == "en") ENGLISH else SPANISH
    }
}

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
    categories: List<Category> = emptyList(),
    labels: FactLabels = FactLabels.SPANISH,
    movements: List<Movement> = emptyList(),
): String = buildString {
    val days = DateTimeFormatter.ofPattern(labels.dayPattern, labels.locale)
    fun line(t: Task) = buildString {
        append("- ").append(t.title)
        t.dueDate?.let { append(" (").append(labels.due).append(" ").append(it.format(days)).append(")") }
        labels.priorities[t.priority]?.let { append(", ").append(labels.priority).append(" ").append(it) }
        t.projectId?.let(projectNames::get)?.let { append(", ").append(labels.project).append(" ").append(it) }
    }
    appendLine(labels.today.format(today.format(days)))
    val overdue = open.filter { it.dueDate != null && it.dueDate < today }
    val dueToday = open.filter { it.dueDate == today }
    val week = open.filter { it.dueDate != null && it.dueDate > today && it.dueDate <= today.plusDays(7) }
    val rest = open - (overdue + dueToday + week).toSet()
    for ((title, list) in listOf(labels.overdue to overdue, labels.dueToday to dueToday, labels.week to week, labels.others to rest)) {
        appendLine("$title (${list.size}):")
        list.forEach { appendLine(line(it)) }
    }
    val byList = items.groupBy { it.listId }
    for (list in lists) {
        fun names(checked: Boolean) = byList[list.id].orEmpty().filter { it.checked == checked }
            .joinToString { i -> i.name + (i.quantity?.let { " ($it)" } ?: "") }.ifEmpty { labels.nothing }
        appendLine(labels.list.format(list.name, names(false)) + " " + labels.inCart.format(names(true)))
    }
    if (month != null) {
        appendLine(labels.finances.format(formatMoney(month.incomeCents), formatMoney(month.expenseCents), formatMoney(month.balanceCents)))
        month.budgetCents?.let { appendLine(labels.budget.format(formatMoney(it))) }
        month.lines.filter { it.spentCents > 0 }.forEach { l ->
            append("- ${l.category.name}: ${labels.spent} ${formatMoney(l.spentCents)}")
            l.category.monthlyBudgetCents?.let { append(" ${labels.of} ${formatMoney(it)}") }
            appendLine()
        }
        val over = month.lines.mapNotNull { l -> l.overCents?.let { "${l.category.name} (${labels.by} ${formatMoney(it)})" } }
        appendLine(labels.overBudget.format(over.joinToString().ifEmpty { labels.none }))
    }
    // Names the model may use for a movement; Finanzas decides expense or income from the category.
    val active = categories.filter { !it.archived }
    for ((kind, title) in listOf(MoneyKind.EXPENSE to labels.expenseCategories, MoneyKind.INCOME to labels.incomeCategories)) {
        active.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { appendLine("$title: ${it.joinToString { c -> c.name }}.") }
    }
    // Worked out here like the month: the model adds up amounts poorly.
    if (movements.isNotEmpty()) {
        val names = categories.associate { it.id to it.name }
        fun byCategory(list: List<Movement>) = list.filter { it.kind == MoneyKind.EXPENSE }.groupBy { it.categoryId }
            .map { (id, ms) -> (names[id] ?: "?") to ms.sumOf { it.amountCents } }.sortedByDescending { it.second }
            .joinToString { "${it.first} ${formatMoney(it.second)}" }.ifEmpty { labels.nothing }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = movements.filter { it.date in weekStart..today && it.kind == MoneyKind.EXPENSE }
        appendLine(labels.thisWeek.format(weekStart.format(days), byCategory(week), formatMoney(week.sumOf { it.amountCents })))
        val previous = YearMonth.from(today).minusMonths(1)
        val last = movements.filter { YearMonth.from(it.date) == previous }
        appendLine(
            labels.lastMonth.format(
                previous.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", labels.locale)),
                formatMoney(last.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amountCents }),
                formatMoney(last.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amountCents }),
                byCategory(last),
            ),
        )
    }
}
