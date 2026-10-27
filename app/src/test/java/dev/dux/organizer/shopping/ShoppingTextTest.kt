package dev.dux.organizer.shopping

import dev.dux.organizer.data.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ShoppingTextTest {
    private fun item(name: String, quantity: String? = null) =
        ShoppingItem(listId = "l", name = name, quantity = quantity, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test fun oneLinePerItemWithOptionalQuantity() {
        assertEquals("Súper:\n- Leche\n- Tortillas (2 kg)", shoppingText("Súper", listOf(item("Leche"), item("Tortillas", "2 kg"))))
    }

    @Test fun emptyListIsJustTheName() {
        assertEquals("Súper:", shoppingText("Súper", emptyList()))
    }
}
