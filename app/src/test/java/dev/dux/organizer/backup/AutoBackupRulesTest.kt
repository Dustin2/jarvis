package dev.dux.organizer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutoBackupRulesTest {
    @Test fun keepsTheNewestAndNeverTouchesOtherFiles() {
        val autos = (1..16).map { autoBackupName(LocalDate.of(2026, 10, it)) }
        val others = listOf("jarvis-respaldo-2026-09-01.json", "jarvis-auto-raro.json", "fotos.zip", "jarvis-auto-2026-01-01.json.bak")
        val deleted = backupsToDelete((autos + others).shuffled())
        assertEquals(listOf("jarvis-auto-2026-10-02.json", "jarvis-auto-2026-10-01.json"), deleted)
        assertEquals(emptyList<String>(), backupsToDelete(autos.take(14)))
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
}
