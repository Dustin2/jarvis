package dev.dux.organizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A section title with optional actions at its end ("Desmarcar todo", "Quitar comprados"). The actions
 * wrap below at large font sizes; in a Row they crushed the title into one letter per line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionTitle(text: String, actions: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).asHeading())
        Row { actions() }
    }
}
