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

// La app solo tiene textos en español; las fechas no deben seguir el idioma del sistema.
val SPANISH: Locale = Locale.forLanguageTag("es-MX")
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)

/** "Hoy", "Mañana", "Ayer" o "lun 27 oct". */
@Composable
fun LocalDate.label(today: LocalDate): String = when (this) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> format(shortDate)
}

/** La fecha de hoy, que se actualiza sola a medianoche aunque la app siga abierta. */
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
