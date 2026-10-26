package dev.dux.organizer

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dux.organizer.ui.JarvisApp
import dev.dux.organizer.ui.theme.JarvisTheme
import org.junit.Rule
import org.junit.Test

class JarvisAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startsOnTodayAndSwitchesTabs() {
        compose.setContent { JarvisTheme { JarvisApp() } }

        compose.onNodeWithText("Siguiente paso").assertExists()

        compose.onNode(hasText("Bandeja") and isSelectable()).performClick().assertIsSelected()
    }
}
