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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.dux.organizer.data.DebtDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Test
    fun deletingADebtFromItsScreenOffersUndoOnTheList() {
        val finance = compose.activity.app.finance
        val person = "Undo " + System.nanoTime().toString().takeLast(5)
        runBlocking { finance.addDebt(person, DebtDirection.I_OWE, 1_000, null) }
        compose.setContent { JarvisTheme { JarvisApp() } }
        fun text(id: Int) = compose.activity.getString(id)
        fun shown(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

        compose.onNode(hasText(text(R.string.finances)) and isSelectable()).performClick()
        compose.onNodeWithText(text(R.string.finance_debts)).performClick()
        compose.waitUntil(5_000) { shown(person) }
        compose.onNodeWithText(person).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(text(R.string.delete_debt)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.delete_debt)).performClick()

        // Back on the list, with "Deshacer".
        val message = compose.activity.getString(R.string.debt_deleted, person)
        compose.waitUntil(5_000) { shown(message) }
        compose.onNodeWithText(text(R.string.undo)).performClick()
        compose.waitUntil(5_000) { !shown(message) && shown(person) }
        runBlocking { finance.debts.first().first { it.debt.person == person }.let { finance.deleteDebt(it.debt.id) } }
    }

    @Test
    fun comprasSuggestsWhatWasBoughtBefore() {
        val shopping = compose.activity.app.shopping
        val item = "Kiwi " + System.nanoTime().toString().takeLast(5)
        // Added once and then removed: only the history remembers it.
        runBlocking {
            shopping.seedIfEmpty()
            val list = shopping.lists.first().first().id
            val id = java.util.UUID.randomUUID().toString()
            shopping.addItem(list, item, id = id)
            shopping.deleteItem(id)
        }
        compose.setContent { JarvisTheme { JarvisApp() } }
        fun text(id: Int) = compose.activity.getString(id)

        compose.onNode(hasText(text(R.string.tab_shopping)) and isSelectable()).performClick()
        compose.onNodeWithContentDescription(text(R.string.add_item)).performClick()
        val focused = hasText(text(R.string.add_item_hint)) and isFocused()
        compose.waitUntil(5_000) { compose.onAllNodes(focused).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(focused).performTextInput(item.take(7).lowercase())
        val chip = "${text(R.string.add)} $item"
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(chip).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(chip).performClick()
        // Added (its check chip shows), and no longer suggested.
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(chip).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(item).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val list = shopping.lists.first().first().id
            shopping.items(list).first().filter { it.name == item }.forEach { shopping.deleteItem(it.id) }
        }
    }
}
