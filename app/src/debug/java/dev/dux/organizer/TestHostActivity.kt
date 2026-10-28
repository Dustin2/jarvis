package dev.dux.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Debug-only host for Compose UI tests. It shows over the lock screen and turns the screen on, like an
 * alarm: with the default host, a test that started while the phone was locked failed with
 * "No compose hierarchies found". Not part of release builds.
 */
class TestHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        super.onCreate(savedInstanceState)
    }
}
