package dev.dux.organizer.ui

import androidx.compose.foundation.layout.Arrangement
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
