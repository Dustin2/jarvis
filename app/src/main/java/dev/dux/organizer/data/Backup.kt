package dev.dux.organizer.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Format documented in docs/backup-format.md. Adding an optional field does not change the version
 * (old files simply do not have it); removing a field or changing its meaning does.
 */
const val BACKUP_FORMAT = "dev.dux.organizer.backup"
const val BACKUP_VERSION = 1

data class BackupData(
    val projects: List<Project>,
    val tasks: List<Task>,
    val shoppingLists: List<ShoppingList> = emptyList(),
    val shoppingItems: List<ShoppingItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val movements: List<Movement> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList(),
)

/** The message is meant to be shown to the user as is (in Spanish). */
class InvalidBackupException(message: String) : Exception(message)

// org.json ships with Android: a handful of tables does not justify adding a serialization library.
fun encodeBackup(data: BackupData, exportedAt: Instant): String = JSONObject()
    .put("format", BACKUP_FORMAT)
    .put("version", BACKUP_VERSION)
    .put("exportedAt", exportedAt.toString())
    .put("projects", JSONArray(data.projects.map { it.toJson() }))
    .put("tasks", JSONArray(data.tasks.map { it.toJson() }))
    .put("shoppingLists", JSONArray(data.shoppingLists.map { it.toJson() }))
    .put("shoppingItems", JSONArray(data.shoppingItems.map { it.toJson() }))
    .put("categories", JSONArray(data.categories.map { it.toJson() }))
    .put("movements", JSONArray(data.movements.map { it.toJson() }))
    .put("debts", JSONArray(data.debts.map { it.toJson() }))
    .put("debtPayments", JSONArray(data.debtPayments.map { it.toJson() }))
    .toString(2)

/** Validates the whole file before returning anything: if something fails, throws [InvalidBackupException]. */
fun decodeBackup(text: String): BackupData {
    val root = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw InvalidBackupException("El archivo no es JSON válido.")
    }
    if (root.optString("format") != BACKUP_FORMAT) throw InvalidBackupException("El archivo no es un respaldo de Jarvis.")
    val version = root.optInt("version", -1)
    if (version > BACKUP_VERSION) throw InvalidBackupException("El respaldo es de una versión más nueva de la app. Actualiza Jarvis.")
    if (version < 1) throw InvalidBackupException("El respaldo no indica una versión válida.")

    val projects = root.list("projects") { i, o -> o.toProject("Proyecto ${i + 1}") }
    val tasks = root.list("tasks") { i, o -> o.toTask("Tarea ${i + 1}") }
    // Optional: backups from before the shopping lists do not have them.
    val shoppingLists = root.list("shoppingLists", required = false) { i, o -> o.toShoppingList("Lista de compras ${i + 1}") }
    val shoppingItems = root.list("shoppingItems", required = false) { i, o -> o.toShoppingItem("Artículo ${i + 1}") }
    // Optional too: backups from before the finances.
    val categories = root.list("categories", required = false) { i, o -> o.toCategory("Categoría ${i + 1}") }
    val movements = root.list("movements", required = false) { i, o -> o.toMovement("Movimiento ${i + 1}") }
    val debts = root.list("debts", required = false) { i, o -> o.toDebt("Deuda ${i + 1}") }
    val debtPayments = root.list("debtPayments", required = false) { i, o -> o.toDebtPayment("Abono ${i + 1}") }

    projects.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos proyectos con el id $it.") }
    tasks.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos tareas con el id $it.") }
    val projectIds = projects.mapTo(HashSet()) { it.id }
    tasks.firstOrNull { it.projectId != null && it.projectId !in projectIds }?.let {
        throw InvalidBackupException("La tarea \"${it.title}\" apunta a un proyecto que no está en el respaldo.")
    }
    shoppingLists.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos listas de compras con el id $it.") }
    shoppingItems.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos artículos con el id $it.") }
    val listIds = shoppingLists.mapTo(HashSet()) { it.id }
    shoppingItems.firstOrNull { it.listId !in listIds }?.let {
        throw InvalidBackupException("El artículo \"${it.name}\" apunta a una lista que no está en el respaldo.")
    }

    categories.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos categorías con el id $it.") }
    movements.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos movimientos con el id $it.") }
    debts.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos deudas con el id $it.") }
    debtPayments.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos abonos con el id $it.") }
    val categoryKinds = categories.associate { it.id to it.kind }
    movements.firstOrNull { categoryKinds[it.categoryId] != it.kind }?.let {
        throw InvalidBackupException("Un movimiento del ${it.date} apunta a una categoría que no está en el respaldo o es de otro tipo.")
    }
    val debtIds = debts.mapTo(HashSet()) { it.id }
    debtPayments.firstOrNull { it.debtId !in debtIds }?.let {
        throw InvalidBackupException("Un abono del ${it.date} apunta a una deuda que no está en el respaldo.")
    }
    return BackupData(projects, tasks, shoppingLists, shoppingItems, categories, movements, debts, debtPayments)
}

private fun Project.toJson() = JSONObject()
    .put("id", id).put("name", name).put("colorKey", colorKey ?: JSONObject.NULL)
    .put("status", status.name).put("sortOrder", sortOrder)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun Task.toJson() = JSONObject()
    .put("id", id).put("title", title).put("notes", notes ?: JSONObject.NULL)
    .put("status", status.name).put("priority", priority.name).put("projectId", projectId ?: JSONObject.NULL)
    .put("dueDate", dueDate?.toString() ?: JSONObject.NULL).put("scheduledDate", scheduledDate?.toString() ?: JSONObject.NULL)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())
    .put("completedAt", completedAt?.toString() ?: JSONObject.NULL)
    .put("reminderAt", reminderAt?.toString() ?: JSONObject.NULL)
    .put("recurrence", recurrence?.format() ?: JSONObject.NULL)
    .put("seriesId", seriesId ?: JSONObject.NULL)

private fun ShoppingList.toJson() = JSONObject()
    .put("id", id).put("name", name).put("sortOrder", sortOrder)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun ShoppingItem.toJson() = JSONObject()
    .put("id", id).put("listId", listId).put("name", name).put("quantity", quantity ?: JSONObject.NULL)
    .put("checked", checked).put("sortOrder", sortOrder)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun Category.toJson() = JSONObject()
    .put("id", id).put("name", name).put("kind", kind.name)
    .put("monthlyBudgetCents", monthlyBudgetCents ?: JSONObject.NULL)
    .put("sortOrder", sortOrder).put("archived", archived)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun Movement.toJson() = JSONObject()
    .put("id", id).put("kind", kind.name).put("amountCents", amountCents).put("categoryId", categoryId)
    .put("date", date.toString()).put("note", note ?: JSONObject.NULL)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun Debt.toJson() = JSONObject()
    .put("id", id).put("person", person).put("direction", direction.name).put("amountCents", amountCents)
    .put("dueDate", dueDate?.toString() ?: JSONObject.NULL).put("note", note ?: JSONObject.NULL)
    .put("settledAt", settledAt?.toString() ?: JSONObject.NULL)
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString())

private fun DebtPayment.toJson() = JSONObject()
    .put("id", id).put("debtId", debtId).put("amountCents", amountCents)
    .put("date", date.toString()).put("createdAt", createdAt.toString())

private fun JSONObject.toProject(where: String) = Field(this, where).run {
    Project(
        id = text("id"),
        name = title("name"),
        colorKey = optionalText("colorKey"),
        status = enum("status", ProjectStatus.entries),
        sortOrder = int("sortOrder"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

private fun JSONObject.toTask(where: String) = Field(this, where).run {
    Task(
        id = text("id"),
        title = title("title"),
        notes = optionalText("notes"),
        status = enum("status", TaskStatus.entries),
        priority = enum("priority", Priority.entries),
        projectId = optionalText("projectId"),
        dueDate = optionalDate("dueDate"),
        scheduledDate = optionalDate("scheduledDate"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
        completedAt = optionalInstant("completedAt"),
        reminderAt = optionalDateTime("reminderAt"),
        recurrence = optionalRecurrence("recurrence"),
        seriesId = optionalText("seriesId"),
    )
}

private fun JSONObject.toShoppingList(where: String) = Field(this, where).run {
    ShoppingList(id = text("id"), name = title("name"), sortOrder = int("sortOrder"), createdAt = instant("createdAt"), updatedAt = instant("updatedAt"))
}

private fun JSONObject.toShoppingItem(where: String) = Field(this, where).run {
    ShoppingItem(
        id = text("id"),
        listId = text("listId"),
        name = title("name"),
        quantity = optionalText("quantity")?.trim()?.ifEmpty { null }?.also {
            if (it.length > ShoppingRepository.MAX_QUANTITY_LENGTH) fail("quantity", "debe tener máximo ${ShoppingRepository.MAX_QUANTITY_LENGTH} caracteres")
        },
        checked = boolean("checked"),
        sortOrder = int("sortOrder"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

private fun JSONObject.toCategory(where: String) = Field(this, where).run {
    val kind = enum("kind", MoneyKind.entries)
    Category(
        id = text("id"),
        name = title("name"),
        kind = kind,
        monthlyBudgetCents = optionalAmount("monthlyBudgetCents")?.also {
            if (kind != MoneyKind.EXPENSE) fail("monthlyBudgetCents", "solo aplica a categorías de gasto")
        },
        sortOrder = int("sortOrder"),
        archived = boolean("archived"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

private fun JSONObject.toMovement(where: String) = Field(this, where).run {
    Movement(
        id = text("id"),
        kind = enum("kind", MoneyKind.entries),
        amountCents = amount("amountCents"),
        categoryId = text("categoryId"),
        date = date("date"),
        note = optionalText("note"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

private fun JSONObject.toDebt(where: String) = Field(this, where).run {
    Debt(
        id = text("id"),
        person = title("person"),
        direction = enum("direction", DebtDirection.entries),
        amountCents = amount("amountCents"),
        dueDate = optionalDate("dueDate"),
        note = optionalText("note"),
        settledAt = optionalInstant("settledAt"),
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

private fun JSONObject.toDebtPayment(where: String) = Field(this, where).run {
    DebtPayment(id = text("id"), debtId = text("debtId"), amountCents = amount("amountCents"), date = date("date"), createdAt = instant("createdAt"))
}

/** Field reading with messages that say exactly what failed and where. */
private class Field(private val o: JSONObject, private val where: String) {
    fun fail(name: String, problem: String): Nothing = throw InvalidBackupException("$where: el campo \"$name\" $problem.")

    fun optionalText(name: String): String? = if (o.isNull(name)) null else o.opt(name) as? String ?: fail(name, "debe ser texto")
    fun text(name: String): String = optionalText(name)?.takeIf { it.isNotBlank() } ?: fail(name, "es obligatorio")
    fun title(name: String): String = cleanTitle(text(name)) ?: fail(name, "debe tener de 1 a $MAX_TITLE_LENGTH caracteres")
    fun int(name: String): Int = (o.opt(name) as? Number)?.toInt() ?: fail(name, "debe ser un número")
    fun optionalAmount(name: String): Long? = if (o.isNull(name)) null else amount(name)
    fun amount(name: String): Long = (o.opt(name) as? Number)?.toLong()?.takeIf { it in 1..MAX_AMOUNT_CENTS && (o.opt(name) !is Double) }
        ?: fail(name, "debe ser un número entero de centavos mayor que 0")
    fun date(name: String): LocalDate = optionalDate(name) ?: fail(name, "es obligatorio")
    fun boolean(name: String): Boolean = o.opt(name) as? Boolean ?: fail(name, "debe ser true o false")
    fun <E : Enum<E>> enum(name: String, values: List<E>): E =
        text(name).let { v -> values.firstOrNull { it.name == v } ?: fail(name, "no admite el valor \"$v\"") }
    fun optionalDate(name: String): LocalDate? = optionalText(name)?.let { parse(name) { LocalDate.parse(it) } }
    fun optionalDateTime(name: String): LocalDateTime? = optionalText(name)?.let { parse(name) { LocalDateTime.parse(it) } }
    fun optionalRecurrence(name: String): Recurrence? =
        optionalText(name)?.let { Recurrence.parse(it) ?: fail(name, "no es una regla de repetición válida") }
    fun optionalInstant(name: String): Instant? = optionalText(name)?.let { parse(name) { Instant.parse(it) } }
    fun instant(name: String): Instant = optionalInstant(name) ?: fail(name, "es obligatorio")

    private fun <T> parse(name: String, block: () -> T): T =
        try { block() } catch (e: Exception) { fail(name, "no tiene un formato de fecha válido") }
}

private fun <T> JSONObject.list(name: String, required: Boolean = true, read: (Int, JSONObject) -> T): List<T> {
    if (!required && !has(name)) return emptyList()
    val array = optJSONArray(name) ?: throw InvalidBackupException("Falta la lista \"$name\".")
    return List(array.length()) { i ->
        read(i, array.optJSONObject(i) ?: throw InvalidBackupException("La lista \"$name\" tiene un elemento inválido."))
    }
}

private fun List<String>.firstDuplicate(): String? {
    val seen = HashSet<String>()
    return firstOrNull { !seen.add(it) }
}
