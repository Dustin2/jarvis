package dev.dux.organizer

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.dux.organizer.TestHostActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import dev.dux.organizer.ui.JarvisApp
import dev.dux.organizer.ui.theme.JarvisTheme
import org.junit.Rule
import org.junit.Test

class JarvisAppTest {
    @get:Rule
    // Shows over the lock screen and turns the screen on, so the test does not depend on the phone's state.
    val compose = createAndroidComposeRule<TestHostActivity>()

    @Test
    fun startsOnTodayAndSwitchesTabsAndTaskViews() {
        compose.setContent { JarvisTheme { JarvisApp() } }
        // Texts from resources: the app follows the phone's language.
        fun text(id: Int) = compose.activity.getString(id)

        compose.onNodeWithText(text(R.string.next_step)).assertExists()

        compose.onNode(hasText(text(R.string.finances)) and isSelectable()).performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.finance_summary)).assertExists()

        // "Por ordenar" (the inbox) is a view inside Tareas ("Por ordenar (n)").
        compose.onNode(hasText(text(R.string.tab_tasks)) and isSelectable()).performClick().assertIsSelected()
        compose.onNode(hasText(text(R.string.status_inbox), substring = true) and isSelectable()).performClick().assertIsSelected()
    }

    @Test
    fun roundAddButtonOnComprasAddsSeveralItemsAtOnce() {
        compose.setContent { JarvisTheme { JarvisApp() } }
        fun text(id: Int) = compose.activity.getString(id)

        compose.onNode(hasText(text(R.string.tab_shopping)) and isSelectable()).performClick()
        // A shopping list is seeded on first run, so the add field is there.
        compose.onNodeWithContentDescription(text(R.string.add_item)).performClick()
        val focused = hasText(text(R.string.add_item_hint)) and isFocused()
        compose.waitUntil(5_000) { compose.onAllNodes(focused).fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNode(focused)
        // "a, b y c" adds three items and the sheet stays open, showing each one.
        val stamp = System.nanoTime().toString().takeLast(5)
        field.performTextInput("Pan $stamp, Leche $stamp y Huevos $stamp")
        field.performImeAction()
        listOf("Pan", "Leche", "Huevos").forEach { item ->
            compose.waitUntil(5_000) { compose.onAllNodesWithText("$item $stamp").fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNode(hasText(text(R.string.add_item_hint)) and hasSetTextAction()).assertExists() // still open
    }

    @Test
    fun backClosesTheKeyboardBeforeTheCaptureSheet() {
        compose.setContent { JarvisTheme { JarvisApp(capture = true) } }
        val title = compose.activity.getString(R.string.capture_title)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun keyboardShown() = instrumentation.uiAutomation.executeShellCommand("dumpsys input_method").let { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }.contains("mInputShown=true")
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(title) and hasSetTextAction()).performClick()
        compose.waitUntil(5_000) { keyboardShown() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !keyboardShown() }
        compose.waitForIdle()
        compose.onNodeWithText(title).assertExists() // the sheet is still open

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }
}
