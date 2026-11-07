package dev.dux.organizer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import dev.dux.organizer.ui.theme.LocalStyle
import dev.dux.organizer.ui.theme.Style
import dev.dux.organizer.R

/**
 * The title row of every main tab, always in the same order: title, search, [extra] actions, and a "⋮"
 * menu that holds the "Nuevo…" entries (docs/design/navigation.md).
 * [menu] receives a callback that closes it. [badge]: a short count ("5 abiertas"). Drawn in the chosen style:
 * Calm is light and quiet, Energy is bold with the count in a pill.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenHeader(
    @StringRes title: Int,
    onSearch: () -> Unit,
    extra: @Composable RowScope.() -> Unit = {},
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    overline: String? = null,
    badge: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val energy = LocalStyle.current == Style.Energy
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 4.dp, top = if (energy) 16.dp else 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            overline?.let {
                if (energy) Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.primary)
                else Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            if (energy) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.asHeading())
                    badge?.let {
                        Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }
            } else {
                Text(stringResource(title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, modifier = Modifier.asHeading())
                badge?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        }
        IconButton(onClick = onSearch) { Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search)) }
        extra()
        if (menu != null) {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options))
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        }
    }
}
