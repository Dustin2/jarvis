package dev.dux.organizer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SavedIndicatorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun showsOnlyAfterASaveAndThenFades() {
        val version = mutableStateOf<Long?>(null)
        compose.setContent { SavedIndicator(version.value) }

        // Loading the record is not a save.
        version.value = 1L
        compose.waitForIdle()
        compose.onNodeWithText("Guardado").assertDoesNotExist()

        version.value = 2L
        compose.waitForIdle()
        compose.onNodeWithText("Guardado").assertExists()

        compose.mainClock.advanceTimeBy(SAVED_VISIBLE_MILLIS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Guardado").assertDoesNotExist()
    }
}
