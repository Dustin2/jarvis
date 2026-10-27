package dev.dux.organizer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R

/**
 * Pick one option out of several. Chips wrap to the next line when they do not fit:
 * with large fonts, segmented buttons cut the text ("Pendi", "Canc").
 */
@Composable
fun <T> Choice(@StringRes title: Int, options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.asHeading())
        ChoiceChips(options, selected, { stringResource(label(it)) }, onSelect)
    }
}

/** The chips alone, for options whose names are data (categories, lists). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                // The check mark shows the selection without relying on color alone.
                leadingIcon = if (isSelected) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )
        }
    }
}
