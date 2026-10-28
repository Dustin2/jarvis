package dev.dux.organizer.backup

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

const val KEEP_BACKUPS = 14
val OVERDUE_AFTER: Duration = Duration.ofDays(3)

// "auto" keeps them apart from manual exports ("jarvis-respaldo-…"), which the cleanup must never delete.
private val autoBackupName = Regex("""^jarvis-auto-\d{4}-\d{2}-\d{2}\.json$""")

fun autoBackupName(date: LocalDate) = "jarvis-auto-$date.json"

/** The automatic backups to delete so only the newest [keep] remain. Any other file is left alone. */
fun backupsToDelete(names: Collection<String>, keep: Int = KEEP_BACKUPS): List<String> =
    names.filter { autoBackupName.matches(it) }.sortedDescending().drop(keep) // ISO dates sort as text

/** True if automatic backup is on and nothing was saved for [OVERDUE_AFTER] (counted from when it was enabled if it never worked). */
fun backupOverdue(lastSuccess: Instant?, enabledAt: Instant?, now: Instant): Boolean {
    val since = lastSuccess ?: enabledAt ?: return false
    return Duration.between(since, now) > OVERDUE_AFTER
}
