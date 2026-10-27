package dev.dux.organizer.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.data.Frequency
import dev.dux.organizer.data.Recurrence
import dev.dux.organizer.ui.Choice
import dev.dux.organizer.ui.SPANISH
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** "Cada mes, el día 10", "Cada 2 semanas: lun, vie"... */
@Composable
fun Recurrence?.describe(): String = when (this?.frequency) {
    null -> stringResource(R.string.repeat_never)
    Frequency.DAILY -> pluralStringResource(R.plurals.repeat_daily, interval, interval)
    Frequency.WEEKLY -> pluralStringResource(R.plurals.repeat_weekly, interval, interval, weekdays.sorted().joinToString(", ") { it.shortName() })
    Frequency.MONTHLY -> pluralStringResource(R.plurals.repeat_monthly, interval, interval, monthDay!!)
}

private fun DayOfWeek.shortName() = getDisplayName(TextStyle.SHORT, SPANISH).trimEnd('.')

/**
 * Edits a repeat rule; [onSave] receives null for "Nunca". Defaults come from [anchor] (the task's date),
 * so a weekly rule starts on that weekday and a monthly one on that day of the month.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(initial: Recurrence?, anchor: LocalDate, onDismiss: () -> Unit, onSave: (Recurrence?) -> Unit) {
    // shortcut: plain remember, so an open dialog resets on rotation; the rule itself is saved instantly.
    var frequency by remember { mutableStateOf(initial?.frequency) }
    var interval by remember { mutableIntStateOf(initial?.interval ?: 1) }
    var weekdays by remember { mutableStateOf(initial?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(anchor.dayOfWeek)) }
    var monthDay by remember { mutableIntStateOf(initial?.monthDay ?: anchor.dayOfMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice(R.string.frequency, listOf(null) + Frequency.entries, frequency, { it.label }, { frequency = it })
                val current = frequency ?: return@Column
                val unit = when (current) {
                    Frequency.DAILY -> R.plurals.unit_days
                    Frequency.WEEKLY -> R.plurals.unit_weeks
                    Frequency.MONTHLY -> R.plurals.unit_months
                }
                Stepper(stringResource(R.string.every), interval, 1..Recurrence.MAX_INTERVAL, pluralStringResource(unit, interval)) { interval = it }
                if (current == Frequency.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                                label = { Text(day.shortName()) },
                            )
                        }
                    }
                }
                if (current == Frequency.MONTHLY) {
                    Stepper(stringResource(R.string.month_day), monthDay, 1..31, null) { monthDay = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(frequency?.let { Recurrence(it, interval, if (it == Frequency.WEEKLY) weekdays else emptySet(), if (it == Frequency.MONTHLY) monthDay else null) })
                },
                // A weekly rule without days is not valid.
                enabled = frequency != Frequency.WEEKLY || weekdays.isNotEmpty(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val Frequency?.label: Int
    get() = when (this) {
        null -> R.string.frequency_never
        Frequency.DAILY -> R.string.frequency_daily
        Frequency.WEEKLY -> R.string.frequency_weekly
        Frequency.MONTHLY -> R.string.frequency_monthly
    }

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, unit: String?, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease))
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase))
        }
        unit?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}
