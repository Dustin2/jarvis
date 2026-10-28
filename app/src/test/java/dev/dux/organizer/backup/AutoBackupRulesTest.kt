package dev.dux.organizer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutoBackupRulesTest {
    private fun file(day: Int, uri: String = "content://drive/$day") = BackupFile(LocalDate.of(2026, 10, day), uri)

    @Test fun keepsTheNewestFourteenItCreated() {
        val existing = (1..14).map { file(it) }
        val (keep, delete) = recordBackup(existing, file(15))
        assertEquals((2..15).map { file(it) }, keep)
        assertEquals(listOf(file(1)), delete)
    }

    @Test fun aSecondBackupTheSameDayReplacesTheEntry() {
        val (keep, delete) = recordBackup(listOf(file(26), file(27, "content://old")), file(27, "content://new"))
        assertEquals(listOf(file(26), file(27, "content://new")), keep)
        assertEquals(emptyList<BackupFile>(), delete)
    }

    @Test fun recordRoundTripsAndSkipsBrokenLines() {
        val files = listOf(file(26), file(27, "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9"))
        assertEquals(files, parseBackupFiles(formatBackupFiles(files)))
        assertEquals(listOf(file(26)), parseBackupFiles("basura\n2026-10-26 content://drive/26\n2026-13-01 content://x"))
        assertEquals(emptyList<BackupFile>(), parseBackupFiles(null))
    }

    @Test fun warnsAfterThreeDaysWithoutABackup() {
        val now = Instant.parse("2026-10-27T12:00:00Z")
        assertFalse("off", backupOverdue(null, null, now))
        assertFalse(backupOverdue(Instant.parse("2026-10-25T12:00:00Z"), null, now))
        assertTrue(backupOverdue(Instant.parse("2026-10-24T11:00:00Z"), null, now))
        // Never worked: counted from when it was turned on.
        assertFalse(backupOverdue(null, Instant.parse("2026-10-26T12:00:00Z"), now))
        assertTrue(backupOverdue(null, Instant.parse("2026-10-20T12:00:00Z"), now))
    }

    @Test fun onlyCloudFoldersWaitForANetwork() {
        assertFalse(needsNetwork("com.android.externalstorage.documents"))
        assertTrue(needsNetwork("com.google.android.apps.docs.storage"))
    }
}
