package dev.dux.organizer.ask

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.Task
import dev.dux.organizer.finances.CategoryLine
import dev.dux.organizer.finances.MonthSummary
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class AskFactsTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val today = LocalDate.of(2026, 10, 28)
    private fun task(title: String, due: LocalDate?) = Task(title = title, dueDate = due, createdAt = now, updatedAt = now)
    private fun category(name: String, budget: Long?) = Category(name = name, kind = MoneyKind.EXPENSE, monthlyBudgetCents = budget, createdAt = now, updatedAt = now)

    @Test
    fun groupsTasksAndListsOverBudgetCategories() {
        val facts = askFacts(
            listOf(task("Pagar internet", today.minusDays(2)), task("Dentista", today), task("Regalo", today.plusDays(3)), task("Leer", null)),
            emptyMap(),
            MonthSummary(100_00, 60_00, listOf(CategoryLine(category("Comida", 40_00), 50_00), CategoryLine(category("Transporte", 20_00), 10_00)), 60_00),
            today,
            listOf(ShoppingList(id = "s", name = "Súper", createdAt = now, updatedAt = now)),
            listOf(
                ShoppingItem(listId = "s", name = "Leche", quantity = "2", createdAt = now, updatedAt = now),
                ShoppingItem(listId = "s", name = "Huevo", checked = true, createdAt = now, updatedAt = now),
            ),
        )
        assertTrue(facts, facts.contains("Tareas vencidas (1):\n- Pagar internet"))
        assertTrue(facts, facts.contains("Tareas para hoy (1):\n- Dentista"))
        assertTrue(facts, facts.contains("Tareas de los próximos 7 días (1):\n- Regalo"))
        assertTrue(facts, facts.contains("Otras tareas pendientes (1):\n- Leer"))
        assertTrue(facts, facts.contains("Categorías donde se pasó del presupuesto: Comida"))
        assertTrue(facts, facts.contains("Lista de compras Súper: por comprar: Leche (2). Ya en el carrito: Huevo."))
        assertTrue(facts, !facts.contains("pasó del presupuesto: Comida (por \$10.00), Transporte"))
    }
}
