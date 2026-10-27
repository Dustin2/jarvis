package dev.dux.organizer.data

import java.util.Locale
import kotlin.math.absoluteValue

/** Upper limit for any single amount: $99,999,999.99. Keeps sums far from overflowing a Long. */
const val MAX_AMOUNT_CENTS = 9_999_999_999L

// A comma followed by one or two digits at the end is a decimal comma ("85,50"); any other comma groups thousands.
private val decimalComma = Regex(",(\\d{1,2})$")
private val plainAmount = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$")

/**
 * Parses what the user typed ("85", "85.5", "85,50", "$1,234.50") into cents.
 * Returns null for anything else, zero, negatives or amounts over [MAX_AMOUNT_CENTS].
 */
fun parseMoney(text: String): Long? {
    var t = text.trim().removePrefix("$").replace(" ", "")
    if (!t.contains('.')) t = t.replace(decimalComma, ".$1")
    t = t.replace(",", "")
    val match = plainAmount.matchEntire(t) ?: return null
    val (whole, fraction) = match.destructured
    if (whole.length > 8) return null
    val cents = whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
    return cents.takeIf { it in 1..MAX_AMOUNT_CENTS }
}

/** "$1,234.50", "-$85.00". Fixed format: Mexican pesos, whatever the phone's language. */
fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = cents.absoluteValue
    return String.format(Locale.US, "%s$%,d.%02d", sign, abs / 100, abs % 100)
}

/** The amount as the user would type it again: "1234.5" → "1234.50", for edit fields. */
fun editableMoney(cents: Long): String = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
