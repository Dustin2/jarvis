package dev.dux.organizer.shopping

import dev.dux.organizer.data.ShoppingHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SuggestionsTest {
    private fun seen(name: String, times: Int, day: Long = 0) = ShoppingHistory(dev.dux.organizer.data.itemKey(name), name, times, Instant.ofEpochSecond(day * 86_400))

    private val history = listOf(seen("Pan", 3, 1), seen("Leche", 5), seen("Huevos", 3, 2), seen("Leche deslactosada", 1))

    @Test fun nothingTypedOffersTheUsualFirst() {
        assertEquals(listOf("Leche", "Huevos", "Pan", "Leche deslactosada", "Café"), suggestItems("", history, listOf("Café", "pan"), onList = emptyList()))
    }

    @Test fun neverWhatIsAlreadyOnTheList() {
        assertEquals(listOf("Huevos", "Pan", "Leche deslactosada"), suggestItems(" ", history, emptyList(), onList = listOf("leche ")))
    }

    @Test fun typingMatchesTheStartIgnoringCaseAndAccents() {
        assertEquals(listOf("Leche", "Leche deslactosada"), suggestItems("LÉ", history, emptyList(), onList = emptyList()))
        assertEquals(listOf("Café"), suggestItems("cafe", history, listOf("Café"), onList = emptyList()))
    }

    @Test fun thenAWordStartingWithIt() {
        assertEquals(listOf("Leche deslactosada"), suggestItems("desl", history, emptyList(), onList = emptyList()))
        assertEquals(emptyList<String>(), suggestItems("xyz", history, emptyList(), onList = emptyList()))
    }

    @Test fun keepsToTheLimit() {
        assertEquals(2, suggestItems("", history, emptyList(), onList = emptyList(), limit = 2).size)
    }
}
