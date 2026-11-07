package dev.dux.organizer.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** On the device: org.json is part of Android, not of the JVM unit tests. */
@RunWith(AndroidJUnit4::class)
class ParseLatestTest {
    private fun release(tag: String, vararg assets: String) =
        """{"tag_name":"$tag","assets":[${assets.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.com/$it"}""" }}]}"""

    @Test fun newerReleaseWithApk() {
        assertEquals(
            Update("0.21.0", "https://example.com/jarvis-v0.21.0.apk"),
            parseLatest(release("v0.21.0", "notes.txt", "jarvis-v0.21.0.apk"), "0.20.0"),
        )
    }

    @Test fun nothingWhenNotNewerOrNoApk() {
        assertNull(parseLatest(release("v0.20.0", "jarvis-v0.20.0.apk"), "0.20.0"))
        assertNull(parseLatest(release("v0.21.0"), "0.20.0"))
        assertNull(parseLatest(release("v0.21.0", "notes.txt"), "0.20.0"))
    }
}
