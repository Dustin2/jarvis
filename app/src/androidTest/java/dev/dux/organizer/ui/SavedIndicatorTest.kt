package dev.dux.organizer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.dux.organizer.R
import dev.dux.organizer.TestHostActivity
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SavedIndicatorTest {
    @get:Rule
    // Shows over the lock screen and turns the screen on, so the test does not depend on the phone's state.
    val compose = createAndroidComposeRule<TestHostActivity>()

    @Test fun showsOnlyAfterASaveAndThenFades() {
        val version = mutableStateOf<Long?>(null)
        compose.setContent { SavedIndicator(version.value) }
        // From resources: the app follows the phone's language.
        val saved = compose.activity.getString(R.string.saved)

        // Loading the record is not a save.
        version.value = 1L
        compose.waitForIdle()
        compose.onNodeWithText(saved).assertDoesNotExist()

        version.value = 2L
        compose.waitForIdle()
        compose.onNodeWithText(saved).assertExists()

        compose.mainClock.advanceTimeBy(SAVED_VISIBLE_MILLIS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText(saved).assertDoesNotExist()
    }
}
