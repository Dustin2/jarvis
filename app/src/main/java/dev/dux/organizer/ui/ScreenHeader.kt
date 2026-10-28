package dev.dux.organizer.ui

import androidx.annotation.StringRes
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
import dev.dux.organizer.R

/**
 * The title row of every main tab, always in the same order: title, search, [extra] actions, and a "⋮"
 * menu that holds the "Nuevo…" entries (docs/design/navigation.md).
 * [menu] receives a callback that closes it.
 */
@Composable
fun ScreenHeader(
    @StringRes title: Int,
    onSearch: () -> Unit,
    extra: @Composable RowScope.() -> Unit = {},
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 4.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).asHeading())
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
