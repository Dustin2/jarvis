package dev.dux.organizer

import dev.dux.organizer.ask.AskStart
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.dux.organizer.ui.JarvisApp
import dev.dux.organizer.ui.theme.JarvisTheme
import dev.dux.organizer.update.UpdateDialog

class MainActivity : ComponentActivity() {
    /** Task to open after tapping a notification; consumed once opened. */
    private val taskToOpen = mutableStateOf<String?>(null)
    /** The widget's "+": open the capture sheet; consumed once shown. */
    private val capture = mutableStateOf(false)
    /** The shopping widget: open Compras on this list; consumed once shown. */
    private val listToOpen = mutableStateOf<String?>(null)
    /** Widget microphone or "Hablar con Jarvis" shortcut (listen), or the morning brief notification; consumed once shown. */
    private val askStart = mutableStateOf<AskStart?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First launch only: on recreation (rotation) the task screen is already in the back stack.
        if (savedInstanceState == null) read(intent)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp(
                    taskToOpen = taskToOpen.value,
                    onTaskOpened = { taskToOpen.value = null },
                    capture = capture.value,
                    onCaptureShown = { capture.value = false },
                    widgetList = listToOpen.value,
                    onListShown = { listToOpen.value = null },
                    askStart = askStart.value,
                    onAskShown = { askStart.value = null },
                )
                UpdateDialog()
            }
        }
    }

    // The app was already open and a notification or the widget was tapped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        taskToOpen.value = intent.getStringExtra(EXTRA_TASK_ID)
        capture.value = intent.getBooleanExtra(EXTRA_CAPTURE, false)
        listToOpen.value = intent.getStringExtra(EXTRA_LIST_ID)
        askStart.value = when (intent.action) {
            ACTION_ASK_VOICE -> AskStart.LISTEN
            ACTION_BRIEF -> AskStart.BRIEF
            else -> null
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_CAPTURE = "capture"
        const val EXTRA_LIST_ID = "listId"
        // An action, not an extra: launcher shortcuts carry the intent action reliably.
        const val ACTION_ASK_VOICE = "dev.dux.organizer.ASK_VOICE"
        const val ACTION_BRIEF = "dev.dux.organizer.BRIEF"
    }
}
