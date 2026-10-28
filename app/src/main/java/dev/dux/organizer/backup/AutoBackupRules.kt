package dev.dux.organizer.backup

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

const val KEEP_BACKUPS = 14
val OVERDUE_AFTER: Duration = Duration.ofDays(3)

// "auto" keeps them apart from manual exports ("jarvis-respaldo-…").
fun autoBackupName(date: LocalDate) = "jarvis-auto-$date.json"

/**
 * A backup file this app created, by its exact document address. The app tracks its own files instead of
 * listing the folder by name: cloud providers such as Google Drive do not list them reliably (and allow
 * duplicate names), and it can never delete a file it did not create.
 */
data class BackupFile(val date: LocalDate, val uri: String)

/** One file per line: "2026-10-27 content://…". Unreadable lines are dropped. */
fun parseBackupFiles(text: String?): List<BackupFile> = text.orEmpty().lines().mapNotNull { line ->
    val (date, uri) = line.split(' ', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
    runCatching { BackupFile(LocalDate.parse(date), uri) }.getOrNull()
}

fun formatBackupFiles(files: List<BackupFile>) = files.joinToString("\n") { "${it.date} ${it.uri}" }

/** Adds today's file (replacing an older entry for the same day) and splits into (keep, delete): only the newest [keep] stay. */
fun recordBackup(files: List<BackupFile>, today: BackupFile, keep: Int = KEEP_BACKUPS): Pair<List<BackupFile>, List<BackupFile>> {
    val all = (files.filter { it.date != today.date } + today).sortedBy { it.date }
    return all.takeLast(keep) to all.dropLast(keep)
}

/** True if automatic backup is on and nothing was saved for [OVERDUE_AFTER] (counted from when it was enabled if it never worked). */
fun backupOverdue(lastSuccess: Instant?, enabledAt: Instant?, now: Instant): Boolean {
    val since = lastSuccess ?: enabledAt ?: return false
    return Duration.between(since, now) > OVERDUE_AFTER
}

/** Phone storage works offline; any other provider (Drive, OneDrive…) needs a connection, so the work waits for one. */
fun needsNetwork(folderAuthority: String?) = folderAuthority != "com.android.externalstorage.documents"
