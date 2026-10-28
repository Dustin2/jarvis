package dev.dux.organizer.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Label and button on one line; with large fonts the button moves down instead of breaking the label.
 * The button is announced with its field ("Fecha límite: Sin fecha"), not only its value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Field(title: Int, value: String, onClick: () -> Unit, menu: @Composable () -> Unit = {}) {
    val label = stringResource(title)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
        Box {
            OutlinedButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "$label: $value" }) { Text(value) }
            menu()
        }
    }
}

/** [onResult] receives the chosen date, or null if "remove" was pressed. */
// DatePicker is still marked experimental, but it is the official M3 picker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, removeLabel: Int, canRemove: Boolean, onDismiss: () -> Unit, onResult: (LocalDate?) -> Unit) {
    // DatePicker works in UTC milliseconds at midnight; always converting in UTC avoids the "one day earlier" bug.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onResult(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.accept)) }
        },
        dismissButton = {
            TextButton(onClick = { onResult(null) }, enabled = canRemove) { Text(stringResource(removeLabel)) }
        },
    ) { DatePicker(state) }
}

/**
 * One planning option of a task (date, priority, project…): icon in a circle, name above, value below.
 * Every row has the same shape, so the list reads evenly; the circle is filled when the option has a
 * value and outlined when it is empty, so what is planned shows at a glance (docs/design/task-detail.md).
 * [menu] is anchored to the row, for options picked from a dropdown.
 */
@Composable
fun PlanRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    value: String,
    isSet: Boolean,
    onClick: () -> Unit,
    menu: @Composable () -> Unit = {},
) {
    val name = stringResource(label)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onClick)
                // Announced as "Fecha límite: Hoy", not as two separate texts.
                .semantics(mergeDescendants = true) { contentDescription = "$name: $value" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.size(40.dp).then(
                    if (isSet) Modifier.background(colors.secondaryContainer, CircleShape) else Modifier.border(1.dp, colors.outline, CircleShape)
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = if (isSet) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = if (isSet) colors.onSurface else colors.onSurfaceVariant)
            }
        }
        menu()
    }
}
