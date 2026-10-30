package dev.dux.organizer.ui

import androidx.compose.ui.platform.LocalConfiguration
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dux.organizer.R
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The language the app's texts are shown in (Spanish or English, from the phone); dates follow it. */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** A date format whose pattern is a string resource, since word order differs ("jueves 29 de octubre", "Thursday, October 29"). */
@Composable
fun rememberDateFormat(@StringRes pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val locale = appLocale()
    return remember(text, locale) { DateTimeFormatter.ofPattern(text, locale) }
}

/** "Hoy", "Mañana", "Ayer" or "lun 27 oct". */
@Composable
fun LocalDate.label(today: LocalDate): String = when (this) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> format(rememberDateFormat(R.string.date_short))
}

/** The same label inside a sentence: "Vence ayer", not "Vence Ayer" (but "Due Thu, Oct 29" keeps its capital). */
@Composable
fun LocalDate.inlineLabel(today: LocalDate): String {
    val locale = appLocale()
    return label(today).let { if (this in today.minusDays(1)..today.plusDays(1)) it.replaceFirstChar { c -> c.lowercase(locale) } else it }
}

/** Today's date, emitted again just after each midnight, so screens left open roll over to the new day. */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.toLocalDate())
        delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
    }
}

/** Today's date, which updates itself at midnight even if the app stays open. */
@Composable
fun rememberToday(): State<LocalDate> = remember { todayFlow() }.collectAsState(LocalDate.now())
