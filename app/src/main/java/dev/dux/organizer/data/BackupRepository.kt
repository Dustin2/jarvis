package dev.dux.organizer.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/**
 * Exporta e importa todos los datos. Trabaja con texto: leer y escribir el archivo elegido por el usuario
 * es trabajo de la pantalla (Storage Access Framework), así esto se prueba sin archivos externos.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val safetyDir: File,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val safetyFile get() = File(safetyDir, "antes-de-restaurar.json")

    val canUndoRestore: Boolean get() = safetyFile.exists()

    // Lectura dentro de una transacción: proyectos y tareas forman una foto coherente.
    suspend fun export(): String = encodeBackup(snapshot(), clock.instant())

    /** Reemplaza todos los datos. Antes guarda los actuales para poder deshacer. */
    suspend fun restore(data: BackupData) {
        val current = export()
        withContext(Dispatchers.IO) {
            safetyDir.mkdirs()
            // Primero a un temporal y luego renombrar: si algo falla a medias, la copia anterior sigue intacta.
            val tmp = File(safetyDir, "antes-de-restaurar.tmp")
            tmp.writeText(current)
            if (!tmp.renameTo(safetyFile)) error("No se pudo guardar la copia de seguridad previa")
        }
        replaceAll(data)
    }

    /** Vuelve a los datos que había antes de la última restauración. */
    suspend fun undoRestore() {
        replaceAll(decodeBackup(withContext(Dispatchers.IO) { safetyFile.readText() }))
        withContext(Dispatchers.IO) { safetyFile.delete() }
    }

    private suspend fun snapshot() = db.withTransaction { BackupData(db.projects().getAll(), db.tasks().getAll()) }

    // Una sola transacción: si falla cualquier inserción, no se borra nada.
    private suspend fun replaceAll(data: BackupData) = db.withTransaction {
        db.tasks().deleteAll()
        db.projects().deleteAll()
        db.projects().insertAll(data.projects)
        db.tasks().insertAll(data.tasks)
    }
}
