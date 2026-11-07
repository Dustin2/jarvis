package dev.dux.organizer.ui.theme

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R

/**
 * The app's personality, picked in Ajustes next to Color and Modo: the same screens with a different font,
 * corner roundness and Mi día header.
 */
enum class Style(@StringRes val label: Int, @StringRes val hint: Int, @FontRes private val font: Int, private val corner: Int) {
    Calm(R.string.style_calm, R.string.style_calm_hint, R.font.manrope, 16),
    Energy(R.string.style_energy, R.string.style_energy_hint, R.font.outfit, 28);

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
