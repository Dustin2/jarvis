package dev.dux.organizer.settings

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.dux.organizer.JarvisApplication
import dev.dux.organizer.R
import dev.dux.organizer.TestHostActivity
import dev.dux.organizer.ui.theme.JarvisTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<TestHostActivity>()

    @Test
    fun indexOpensEachPageAndBackReturns() {
        val app = compose.activity.application as JarvisApplication
        val vm = SettingsViewModel(app.backups, app.contentResolver, app.autoBackup, app.askJarvis, app.morningBrief, app.appearance)
        compose.setContent { JarvisTheme { SettingsScreen(vm, onBack = {}) } }
        fun text(id: Int) = compose.activity.getString(id)

        compose.onNodeWithText(text(R.string.appearance)).performClick()
        compose.onNodeWithText(text(R.string.style)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()

        compose.onNodeWithText(text(R.string.backup_restore)).performClick()
        compose.onNodeWithText(text(R.string.export_backup)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()

        compose.onNodeWithText(text(R.string.about)).performScrollTo().performClick()
        compose.onNodeWithText("github.com/Dustin2/jarvis").assertExists()
    }
}
