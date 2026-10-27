package dev.dux.organizer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesWhatPeopleType() {
        mapOf(
            "85" to 8500L, "85.5" to 8550L, "85.50" to 8550L, "85,50" to 8550L, "85,5" to 8550L,
            "$1,234.50" to 123450L, "1,234" to 123400L, "1,234,567" to 123456700L, " 0.01 " to 1L, "$ 20" to 2000L,
        ).forEach { (text, cents) -> assertEquals(text, cents, parseMoney(text)) }
    }

    @Test fun rejectsInvalidAmounts() {
        listOf("", "abc", "-5", "0", "0.00", "1.234", "12.345", "1e5", "100000000", "5.", ".5")
            .forEach { assertNull(it, parseMoney(it)) }
        assertEquals(MAX_AMOUNT_CENTS, parseMoney("99999999.99"))
    }

    @Test fun formatsAsMexicanPesos() {
        assertEquals("$1,234.50", formatMoney(123450))
        assertEquals("$0.05", formatMoney(5))
        assertEquals("-$85.00", formatMoney(-8500))
        assertEquals("1234.50", editableMoney(123450))
        assertEquals(123450L, parseMoney(editableMoney(123450)))
    }
}
