package dev.dux.organizer.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dux.organizer.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(@StringRes val label: Int) { System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark) }

data class AppearanceSettings(val mode: ThemeMode = ThemeMode.System, val palette: Palette = Palette.Wallpaper)

private val Context.appearanceStore by preferencesDataStore("appearance")
private val MODE = stringPreferencesKey("mode")
private val PALETTE = stringPreferencesKey("palette")

/** Light/dark and colors chosen in Ajustes. Stored by name; an unknown name (from a newer version) falls back to the default. */
class Appearance(private val context: Context) {
    val settings: Flow<AppearanceSettings> = context.appearanceStore.data.map { p ->
        AppearanceSettings(
            ThemeMode.entries.find { it.name == p[MODE] } ?: ThemeMode.System,
            Palette.entries.find { it.name == p[PALETTE] } ?: Palette.Wallpaper,
        )
    }

    suspend fun setMode(mode: ThemeMode) = context.appearanceStore.edit { it[MODE] = mode.name }
    suspend fun setPalette(palette: Palette) = context.appearanceStore.edit { it[PALETTE] = palette.name }
}
