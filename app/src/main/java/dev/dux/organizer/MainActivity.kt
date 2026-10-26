package dev.dux.organizer

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.dux.organizer.ui.JarvisApp
import dev.dux.organizer.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app solo está en español: sin esto, componentes como el calendario siguen el idioma del sistema.
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) applicationLocales = LocaleList.forLanguageTags("es-MX")
        }
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisApp()
            }
        }
    }
}
