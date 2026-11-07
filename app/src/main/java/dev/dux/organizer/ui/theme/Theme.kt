package dev.dux.organizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * [palette]: the colors picked in Ajustes; [Palette.Wallpaper] is Dynamic Color, always available
 * (minSdk 33 ≥ 31). [darkTheme] follows the phone unless the user picked light or dark.
 */
@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: Palette = Palette.Wallpaper,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        palette == Palette.Wallpaper && darkTheme -> dynamicDarkColorScheme(context)
        palette == Palette.Wallpaper -> dynamicLightColorScheme(context)
        darkTheme -> palette.dark!!
        else -> palette.light!!
    }
    // Over budget is a warning, not an error: orange, told apart from blue by lightness too, and always with text.
    val warning = if (darkTheme) Color(0xFFFFB68A) else Color(0xFF8B4A00)
    CompositionLocalProvider(LocalWarningColor provides warning) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/** Orange for "watch out" states (over budget); Material's error red is kept for real errors. */
val LocalWarningColor = staticCompositionLocalOf { Color(0xFF8B4A00) }
