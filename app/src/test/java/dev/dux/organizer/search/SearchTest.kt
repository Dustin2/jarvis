package dev.dux.organizer.search

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.Debt
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SearchTest {
    private val t = Instant.EPOCH

    @Test fun ignoresCaseAndAccents() {
        assertEquals("cafe jose", normalize("Café JOSÉ"))
        assertEquals(0..3, matchRange("Café con leche", "CAFE"))
        assertEquals(5..8, matchRange("Pago José", "jose"))
        assertEquals(9..13, matchRange("Comprar  Leche", "leche"))
        assertNull(matchRange("Leche", "pan"))
        assertNull(matchRange("Leche", "  "))
    }

    @Test fun findsInEverySectionAndSortsUsefulFirst() {
        val data = SearchData(
            tasks = listOf(
                Task(id = "done", title = "Pagar internet", status = TaskStatus.DONE, createdAt = t, updatedAt = t.plusSeconds(9)),
                Task(id = "open", title = "Llamar", notes = "por el Internet de casa", status = TaskStatus.TODO, createdAt = t, updatedAt = t),
                Task(id = "other", title = "Gimnasio", createdAt = t, updatedAt = t),
            ),
            lists = listOf(ShoppingList(id = "l", name = "Súper", createdAt = t, updatedAt = t)),
            items = listOf(ShoppingItem(id = "i", listId = "l", name = "Cable de internet", createdAt = t, updatedAt = t)),
            categories = listOf(Category(id = "c", name = "Servicios", kind = MoneyKind.EXPENSE, createdAt = t, updatedAt = t)),
            movements = listOf(
                Movement(id = "old", kind = MoneyKind.EXPENSE, amountCents = 1, categoryId = "c", date = LocalDate.of(2026, 9, 1), note = "Internet", createdAt = t, updatedAt = t),
                Movement(id = "new", kind = MoneyKind.EXPENSE, amountCents = 1, categoryId = "c", date = LocalDate.of(2026, 10, 1), note = "internet", createdAt = t, updatedAt = t),
                Movement(id = "byCategory", kind = MoneyKind.EXPENSE, amountCents = 1, categoryId = "c", date = LocalDate.of(2026, 8, 1), createdAt = t, updatedAt = t),
            ),
            debts = listOf(Debt(person = "José", direction = DebtDirection.I_OWE, amountCents = 1, createdAt = t, updatedAt = t)),
        )

        val results = search("INTÉRNET", data)
        assertEquals("open tasks first, then finished", listOf("open", "done"), results.tasks.map { it.id })
        assertEquals(listOf(ItemHit(data.items[0], "Súper")), results.items)
        assertEquals("newest first", listOf("new", "old"), results.movements.map { it.movement.id })
        assertTrue(results.debts.isEmpty())

        assertEquals(listOf("byCategory", "old", "new").sorted(), search("servicios", data).movements.map { it.movement.id }.sorted())
        assertEquals("José", search("jose", data).debts.single().person)
        assertTrue(search("  ", data).isEmpty)
    }
}
