package dev.dux.organizer.data

import androidx.room.withTransaction
import dev.dux.organizer.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/**
 * Exports and imports all data. Works with text: reading and writing the file the user picks
 * is the screen's job (Storage Access Framework), so this can be tested without external files.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val safetyDir: File,
    private val clock: Clock = Clock.systemUTC(),
    private val reminders: Reminders? = null,
) {
    private val safetyFile get() = File(safetyDir, "before-restore.json")

    val canUndoRestore: Boolean get() = safetyFile.exists()

    // Read inside a transaction: projects and tasks form a consistent snapshot.
    suspend fun export(): String = encodeBackup(snapshot(), clock.instant())

    /** Replaces all data. Saves the current data first so it can be undone. */
    suspend fun restore(data: BackupData) {
        val current = export()
        withContext(Dispatchers.IO) {
            safetyDir.mkdirs()
            // Write to a temp file, then rename: if something fails halfway, the previous copy stays intact.
            val tmp = File(safetyDir, "before-restore.tmp")
            tmp.writeText(current)
            if (!tmp.renameTo(safetyFile)) error("No se pudo guardar la copia de seguridad previa")
        }
        replaceAll(data)
    }

    /** Goes back to the data from before the last restore. */
    suspend fun undoRestore() {
        replaceAll(decodeBackup(withContext(Dispatchers.IO) { safetyFile.readText() }))
        withContext(Dispatchers.IO) { safetyFile.delete() }
    }

    private suspend fun snapshot() = db.withTransaction { BackupData(db.projects().getAll(), db.tasks().getAll()) }

    private suspend fun replaceAll(data: BackupData) {
        // A single transaction: if any insert fails, nothing is deleted.
        db.withTransaction {
            db.tasks().deleteAll()
            db.projects().deleteAll()
            db.projects().insertAll(data.projects)
            db.tasks().insertAll(data.tasks)
        }
        // Alarms of tasks that no longer exist discard themselves when they fire (the task is not found).
        reminders?.syncAll()
    }
}
