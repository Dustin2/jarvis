package dev.dux.organizer

import android.app.LocaleManager
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.dux.organizer.ui.JarvisApp
import dev.dux.organizer.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    /** Task to open after tapping a notification; consumed once opened. */
    private val taskToOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is Spanish-only: without this, components such as the date picker follow the system language.
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) applicationLocales = LocaleList.forLanguageTags("es-MX")
        }
        // First launch only: on recreation (rotation) the task screen is already in the back stack.
        if (savedInstanceState == null) taskToOpen.value = intent.getStringExtra(EXTRA_TASK_ID)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp(taskToOpen = taskToOpen.value, onTaskOpened = { taskToOpen.value = null })
            }
        }
    }

    // The app was already open and a notification was tapped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskToOpen.value = intent.getStringExtra(EXTRA_TASK_ID)
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
    }
}
