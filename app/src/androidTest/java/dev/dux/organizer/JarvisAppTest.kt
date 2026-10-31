package dev.dux.organizer

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.dux.organizer.TestHostActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        // The Bandeja is a view inside Tareas ("Bandeja (n)").
        compose.onNode(hasText(text(R.string.tab_tasks)) and isSelectable()).performClick().assertIsSelected()
        compose.onNode(hasText(text(R.string.status_inbox), substring = true) and isSelectable()).performClick().assertIsSelected()
    }
}
