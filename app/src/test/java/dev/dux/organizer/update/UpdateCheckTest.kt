package dev.dux.organizer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun comparesVersionsByNumberNotText() {
        assertTrue(isNewer("v0.21.0", "0.20.0"))
        assertTrue(isNewer("v0.20.1", "0.20.0"))
        assertTrue(isNewer("v1.0.0", "0.99.9"))
        assertTrue(isNewer("v0.10.0", "0.9.0"))
        assertTrue(isNewer("v0.20.0.1", "0.20.0"))
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(isNewer("v0.20.0", "0.20.0"))
        assertFalse(isNewer("v0.19.3", "0.20.0"))
        assertFalse(isNewer("v0.20", "0.20.0"))
        assertFalse(isNewer("", "0.20.0"))
        assertFalse(isNewer("v0.20.0-beta", "0.20.0"))
    }
}
