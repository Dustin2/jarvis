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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.dux.organizer.app
import dev.dux.organizer.data.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
private val FILES = stringPreferencesKey("files")
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
    // Enabling backs up right away while WorkManager starts the first daily run: without this, both see
    // "no file today yet" and create two.
    private val running = Mutex()

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
            // The files in the old folder are no longer ours to replace or delete.
            if (previous != folder.toString()) it.remove(FILES)
        }
        if (previous != null && previous != folder.toString()) release(previous)
        schedule(folder)
        return runNow()
    }

    /** At app start: applies the current scheduling rules to a backup enabled by an older version. */
    suspend fun scheduleIfEnabled() {
        store.data.first()[FOLDER]?.let { schedule(it.toUri()) }
    }

    private fun schedule(folder: Uri) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(if (needsNetwork(folder.authority)) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
        // UPDATE: a new folder may change the constraints, but the next run is not pushed further away.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build(),
        )
    }

    suspend fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        store.data.first()[FOLDER]?.let(::release)
        store.edit { it.clear() }
    }

    /** Writes today's backup (replacing the one from earlier today) and deletes the old ones. Returns false if it failed. */
    suspend fun runNow(): Boolean = running.withLock {
        val prefs = store.data.first()
        val folder = prefs[FOLDER]?.toUri() ?: return@withLock true // turned off: nothing to do
        try {
            val json = backups.export()
            val today = LocalDate.now(clock)
            val files = parseBackupFiles(prefs[FILES])
            val keep = withContext(Dispatchers.IO) {
                val earlier = files.lastOrNull { it.date == today }?.uri?.toUri()
                val file = earlier?.takeIf { overwrite(it, json) } ?: run {
                    // Could not rewrite today's file (deleted, or the provider refuses): replace it, never leave two.
                    earlier?.let(::deleteQuietly)
                    create(folder, autoBackupName(today), json)
                }
                val (keep, delete) = recordBackup(files, BackupFile(today, file.toString()))
                delete.forEach { deleteQuietly(it.uri.toUri()) }
                keep
            }
            store.edit {
                it[FILES] = formatBackupFiles(keep)
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

    private fun create(tree: Uri, name: String, json: String): Uri {
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(resolver, folder, "application/json", name) ?: error("cannot create the file")
        write(file, json)
        return file
    }

    private fun overwrite(file: Uri, json: String): Boolean = try {
        write(file, json)
        true
    } catch (e: Exception) {
        false
    }

    // "wt" truncates: with "w" some providers leave garbage at the end of a longer old file.
    private fun write(file: Uri, json: String) {
        resolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) } ?: error("cannot open the file")
    }

    private fun deleteQuietly(file: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, file)
        } catch (e: Exception) {
            // Already gone, or the provider refuses: an extra old file is harmless.
        }
    }
}

private fun Long.instant() = Instant.ofEpochMilli(this)

/** Run by WorkManager once a day, even with the app closed. A failure is retried with backoff. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (applicationContext.app.autoBackup.runNow()) Result.success() else Result.retry()
}
