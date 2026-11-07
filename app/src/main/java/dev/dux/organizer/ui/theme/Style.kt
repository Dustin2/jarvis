package dev.dux.organizer.ui.theme

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R

/**
 * The app's personality, picked in Ajustes next to Color and Modo: the same screens with a different font,
 * corner roundness and Mi día header. Night is always dark.
 */
enum class Style(@StringRes val label: Int, @StringRes val hint: Int, @FontRes private val font: Int, private val corner: Int) {
    Calm(R.string.style_calm, R.string.style_calm_hint, R.font.manrope, 16),
    Energy(R.string.style_energy, R.string.style_energy_hint, R.font.outfit, 28),
    Night(R.string.style_night, R.string.style_night_hint, R.font.sora, 22);

    // Variable fonts: one file, each weight set through the wght axis.
    private val family by lazy {
        FontFamily(listOf(300, 400, 500, 600, 700, 800).map { w ->
            Font(font, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        })
    }

    val typography: Typography by lazy {
        val t = Typography()
        fun TextStyle.f() = copy(fontFamily = family)
        Typography(
            t.displayLarge.f(), t.displayMedium.f(), t.displaySmall.f(),
            t.headlineLarge.f(), t.headlineMedium.f(), t.headlineSmall.f(),
            t.titleLarge.f(), t.titleMedium.f(), t.titleSmall.f(),
            t.bodyLarge.f(), t.bodyMedium.f(), t.bodySmall.f(),
            t.labelLarge.f(), t.labelMedium.f(), t.labelSmall.f(),
        )
    }

    val shapes: Shapes by lazy {
        Shapes(
            extraSmall = RoundedCornerShape((corner / 4).dp),
            small = RoundedCornerShape((corner / 2).dp),
            medium = RoundedCornerShape((corner * 3 / 4).dp),
            large = RoundedCornerShape(corner.dp),
            extraLarge = RoundedCornerShape((corner + 8).dp),
        )
    }
}

val LocalStyle = staticCompositionLocalOf { Style.Energy }

/** Night: the palette's accents on a near-black, slightly blue background instead of Material's gray. */
internal fun ColorScheme.night() = copy(
    background = Color(0xFF0E1116),
    surface = Color(0xFF0E1116),
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = Color(0xFF13171D),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF1E232C),
    surfaceContainerHighest = Color(0xFF262C36),
    outlineVariant = Color(0xFF2A313C),
)
