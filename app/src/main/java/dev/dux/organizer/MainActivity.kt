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
    /** Tarea a abrir al tocar una notificación; se consume una vez abierta. */
    private val taskToOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app solo está en español: sin esto, componentes como el calendario siguen el idioma del sistema.
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) applicationLocales = LocaleList.forLanguageTags("es-MX")
        }
        // Solo en el primer arranque: al recrear (rotación) la pantalla de la tarea ya está en el back stack.
        if (savedInstanceState == null) taskToOpen.value = intent.getStringExtra(EXTRA_TASK_ID)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp(taskToOpen = taskToOpen.value, onTaskOpened = { taskToOpen.value = null })
            }
        }
    }

    // La app ya estaba abierta y se tocó una notificación.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskToOpen.value = intent.getStringExtra(EXTRA_TASK_ID)
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
    }
}
