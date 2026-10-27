package dev.dux.organizer.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.dux.organizer.app
import dev.dux.organizer.data.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val Context.autoBackupStore by preferencesDataStore("auto_backup")
private val FOLDER = stringPreferencesKey("folder")
private val ENABLED_AT = longPreferencesKey("enabledAt")
private val LAST_SUCCESS = longPreferencesKey("lastSuccess")
private val LAST_FAILURE = longPreferencesKey("lastFailure")
private const val WORK = "auto-backup"

/** [lastFailure] is only set while the last attempt failed. */
data class AutoBackupState(val folder: Uri?, val enabledAt: Instant?, val lastSuccess: Instant?, val lastFailure: Instant?)

/**
 * Daily JSON backup into a folder the user picks once (Storage Access Framework), keeping the newest
 * [KEEP_BACKUPS]. Design: docs/design/auto-backup.md.
 */
class AutoBackup(private val context: Context, private val backups: BackupRepository, private val clock: Clock = Clock.systemDefaultZone()) {
    private val store = context.autoBackupStore
    private val resolver get() = context.contentResolver

    val state: Flow<AutoBackupState> = store.data.map { p ->
        AutoBackupState(p[FOLDER]?.toUri(), p[ENABLED_AT]?.instant(), p[LAST_SUCCESS]?.instant(), p[LAST_FAILURE]?.instant())
    }

    /** Keeps access to [folder] across restarts, schedules the daily work and backs up right away. */
    suspend fun enable(folder: Uri): Boolean {
        resolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val previous = store.data.first()[FOLDER]
        store.edit {
            it[FOLDER] = folder.toString()
            it[ENABLED_AT] = clock.millis()
            it.remove(LAST_FAILURE)
        }
        if (previous != null && previous != folder.toString()) release(previous)
        // KEEP: re-enabling does not push the next run further away.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        return runNow()
    }

    suspend fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        store.data.first()[FOLDER]?.let(::release)
        store.edit { it.clear() }
    }

    /** Writes today's backup (replacing one from earlier today) and deletes the old ones. Returns false if it failed. */
    suspend fun runNow(): Boolean {
        val folder = store.data.first()[FOLDER]?.toUri() ?: return true // turned off: nothing to do
        return try {
            val json = backups.export()
            withContext(Dispatchers.IO) {
                write(folder, autoBackupName(LocalDate.now(clock)), json)
                val files = children(folder)
                backupsToDelete(files.keys).forEach { DocumentsContract.deleteDocument(resolver, files.getValue(it)) }
            }
            store.edit {
                it[LAST_SUCCESS] = clock.millis()
                it.remove(LAST_FAILURE)
            }
            true
        } catch (e: Exception) {
            // Folder deleted, permission revoked, storage full… The screen explains it in Spanish.
            store.edit { it[LAST_FAILURE] = clock.millis() }
            false
        }
    }

    private fun release(folder: String) {
        try {
            resolver.releasePersistableUriPermission(folder.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already gone (e.g. the folder was deleted): nothing to release.
        }
    }

    private fun folderDocument(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** File name → document, for the files directly inside the folder. */
    private fun children(tree: Uri): Map<String, Uri> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, columns, null, null, null)?.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))) }
        } ?: error("cannot list the folder")
    }

    private fun write(tree: Uri, name: String, json: String) {
        // Reuse today's file: creating it again would make "name (1).json".
        val file = children(tree)[name]
            ?: DocumentsContract.createDocument(resolver, folderDocument(tree), "application/json", name)
            ?: error("cannot create the file")
        // "wt" truncates: with "w" some providers leave garbage at the end of a longer old file.
        resolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) } ?: error("cannot open the file")
    }
}

private fun Long.instant() = Instant.ofEpochMilli(this)

/** Run by WorkManager once a day, even with the app closed. A failure is retried with backoff. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (applicationContext.app.autoBackup.runNow()) Result.success() else Result.retry()
}
