package dev.dux.organizer.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Formato documentado en docs/backup-format.md. Agregar un campo opcional no cambia la versión
 * (los archivos viejos simplemente no lo traen); quitar o cambiar el significado de uno, sí.
 */
const val BACKUP_FORMAT = "dev.dux.organizer.backup"
const val BACKUP_VERSION = 1

data class BackupData(val projects: List<Project>, val tasks: List<Task>)

/** El mensaje está pensado para mostrarse tal cual al usuario. */
class InvalidBackupException(message: String) : Exception(message)

// org.json viene con Android: para dos tablas no justifica agregar una librería de serialización.
fun encodeBackup(data: BackupData, exportedAt: Instant): String = JSONObject()
    .put("format", BACKUP_FORMAT)
    .put("version", BACKUP_VERSION)
    .put("exportedAt", exportedAt.toString())
    .put("projects", JSONArray(data.projects.map { it.toJson() }))
    .put("tasks", JSONArray(data.tasks.map { it.toJson() }))
    .toString(2)

/** Valida el archivo completo antes de devolver nada: si algo falla, lanza [InvalidBackupException]. */
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

    projects.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos proyectos con el id $it.") }
    tasks.map { it.id }.firstDuplicate()?.let { throw InvalidBackupException("Hay dos tareas con el id $it.") }
    val projectIds = projects.mapTo(HashSet()) { it.id }
    tasks.firstOrNull { it.projectId != null && it.projectId !in projectIds }?.let {
        throw InvalidBackupException("La tarea \"${it.title}\" apunta a un proyecto que no está en el respaldo.")
    }
    return BackupData(projects, tasks)
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
    )
}

/** Lectura de campos con mensajes que dicen exactamente qué falló y dónde. */
private class Field(private val o: JSONObject, private val where: String) {
    private fun fail(name: String, problem: String): Nothing = throw InvalidBackupException("$where: el campo \"$name\" $problem.")

    fun optionalText(name: String): String? = if (o.isNull(name)) null else o.opt(name) as? String ?: fail(name, "debe ser texto")
    fun text(name: String): String = optionalText(name)?.takeIf { it.isNotBlank() } ?: fail(name, "es obligatorio")
    fun title(name: String): String = cleanTitle(text(name)) ?: fail(name, "debe tener de 1 a $MAX_TITLE_LENGTH caracteres")
    fun int(name: String): Int = (o.opt(name) as? Number)?.toInt() ?: fail(name, "debe ser un número")
    fun <E : Enum<E>> enum(name: String, values: List<E>): E =
        text(name).let { v -> values.firstOrNull { it.name == v } ?: fail(name, "no admite el valor \"$v\"") }
    fun optionalDate(name: String): LocalDate? = optionalText(name)?.let { parse(name) { LocalDate.parse(it) } }
    fun optionalDateTime(name: String): LocalDateTime? = optionalText(name)?.let { parse(name) { LocalDateTime.parse(it) } }
    fun optionalInstant(name: String): Instant? = optionalText(name)?.let { parse(name) { Instant.parse(it) } }
    fun instant(name: String): Instant = optionalInstant(name) ?: fail(name, "es obligatorio")

    private fun <T> parse(name: String, block: () -> T): T =
        try { block() } catch (e: Exception) { fail(name, "no tiene un formato de fecha válido") }
}

private fun <T> JSONObject.list(name: String, read: (Int, JSONObject) -> T): List<T> {
    val array = optJSONArray(name) ?: throw InvalidBackupException("Falta la lista \"$name\".")
    return List(array.length()) { i ->
        read(i, array.optJSONObject(i) ?: throw InvalidBackupException("La lista \"$name\" tiene un elemento inválido."))
    }
}

private fun List<String>.firstDuplicate(): String? {
    val seen = HashSet<String>()
    return firstOrNull { !seen.add(it) }
}
