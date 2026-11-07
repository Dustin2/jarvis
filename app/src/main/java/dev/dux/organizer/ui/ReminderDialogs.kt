package dev.dux.organizer.ui

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.dux.organizer.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Picks a reminder: date, then time. [onResult] gets the chosen moment, or null for "Quitar recordatorio";
 * a moment already gone goes to [onPast] instead. Shown while the caller keeps it in the composition.
 */
// TimePicker is still marked experimental, but it is the official M3 picker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialogs(at: LocalDateTime?, today: LocalDate, onDismiss: () -> Unit, onPast: () -> Unit, onResult: (LocalDateTime?) -> Unit) {
    val context = LocalContext.current
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val date = pickedDate
    if (date == null) {
        DatePickDialog(at?.toLocalDate() ?: today, R.string.remove_reminder, canRemove = at != null, onDismiss = onDismiss) {
            if (it == null) onResult(null) else pickedDate = it
        }
        return
    }
    val initial = at?.toLocalTime() ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                val chosen = date.atTime(state.hour, state.minute)
                if (chosen <= LocalDateTime.now()) onPast() else onResult(chosen)
            }) { Text(stringResource(R.string.accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Mañana, 9:00", in the phone's 12/24-hour format. */
@Composable
fun reminderLabel(at: LocalDateTime, today: LocalDate): String {
    val context = LocalContext.current
    val locale = appLocale()
    val time = remember(locale) { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", locale) }
    return "${at.toLocalDate().label(today)}, ${at.toLocalTime().format(time)}"
}
