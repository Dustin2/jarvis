package dev.dux.organizer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.reminders.snoozeOptions
import java.time.LocalDateTime

/**
 * "Posponer" from a reminder: one-tap choices (En 1 hora, Esta tarde, Mañana, El lunes) and, last, the date
 * and time pickers. [onResult] gets the new moment; [onPast] if the picked one is already gone.
 */
// ModalBottomSheet is still marked experimental, but it is the official M3 pattern.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(onDismiss: () -> Unit, onPast: () -> Unit, onResult: (LocalDateTime) -> Unit) {
    val today by rememberToday()
    var picking by rememberSaveable { mutableStateOf(false) }
    // Computed once: the times do not shift while the sheet is open.
    val options = remember { snoozeOptions(LocalDateTime.now()) }
    if (picking) {
        ReminderDialogs(null, today, onDismiss = { picking = false }, onPast = onPast) { at -> if (at != null) onResult(at) else picking = false }
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.postpone), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            options.forEach { (option, at) ->
                ListItem(
                    headlineContent = { Text(stringResource(option.label)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_bell), contentDescription = null, Modifier.size(24.dp)) },
                    trailingContent = { Text(reminderLabel(at, today), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = colors,
                    modifier = Modifier.padding(horizontal = 8.dp).clickable(role = Role.Button) { onResult(at) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.snooze_pick), color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                colors = colors,
                modifier = Modifier.padding(horizontal = 8.dp).clickable(role = Role.Button) { picking = true },
            )
        }
    }
}
