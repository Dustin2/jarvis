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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.dux.organizer.ui.theme.LocalStyle
import dev.dux.organizer.ui.theme.Style

/**
 * A section title with optional actions at its end ("Desmarcar todo", "Quitar comprados"). The actions
 * wrap below at large font sizes; in a Row they crushed the title into one letter per line.
 * Calm: small spaced capitals, quiet. Energy: large and bold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionTitle(text: String, actions: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalStyle.current == Style.Energy) {
            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp).asHeading())
        } else {
            // Read as written: TalkBack may spell out short all-caps words letter by letter.
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).asHeading().semantics { contentDescription = text },
            )
        }
        Row { actions() }
    }
}
