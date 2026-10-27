package dev.dux.organizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dux.organizer.R
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// The app's texts are Spanish only; dates must not follow the system language.
val SPANISH: Locale = Locale.forLanguageTag("es-MX")
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)

/** "Hoy", "Mañana", "Ayer" or "lun 27 oct". */
@Composable
fun LocalDate.label(today: LocalDate): String = when (this) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> format(shortDate)
}

/** Today's date, which updates itself at midnight even if the app stays open. */
@Composable
fun rememberToday(): State<LocalDate> {
    val today = remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
            today.value = LocalDate.now()
        }
    }
    return today
}
