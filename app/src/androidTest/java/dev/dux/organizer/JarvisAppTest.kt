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
    fun startsOnTodayAndSwitchesTabsAndTaskViews() {
        compose.setContent { JarvisTheme { JarvisApp() } }

        compose.onNodeWithText("Siguiente paso").assertExists()

        compose.onNode(hasText("Finanzas") and isSelectable()).performClick().assertIsSelected()
        compose.onNodeWithText("Resumen").assertExists()

        // The Bandeja is a view inside Tareas ("Bandeja (n)").
        compose.onNode(hasText("Tareas") and isSelectable()).performClick().assertIsSelected()
        compose.onNode(hasText("Bandeja", substring = true) and isSelectable()).performClick().assertIsSelected()
    }
}
