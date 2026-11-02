package dev.dux.organizer.ask

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
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

    @Test
    fun englishFactsUseEnglishWordsAndDates() {
        val facts = askFacts(
            listOf(task("Pay the internet bill", today.minusDays(2))),
            emptyMap(),
            null,
            today,
            labels = FactLabels.ENGLISH,
        )
        assertTrue(facts, facts.startsWith("Today is Wednesday, October 28, 2026."))
        assertTrue(facts, facts.contains("Overdue tasks (1):\n- Pay the internet bill (due Monday, October 26, 2026)"))
    }

    @Test
    fun spendingThisWeekAndLastMonth() {
        // today is Wednesday 2026-10-28: the week starts Monday the 26th.
        val food = Category(id = "f", name = "Comida", kind = MoneyKind.EXPENSE, createdAt = now, updatedAt = now)
        val salary = Category(id = "s", name = "Sueldo", kind = MoneyKind.INCOME, createdAt = now, updatedAt = now)
        fun m(cents: Long, cat: Category, date: LocalDate) = Movement(kind = cat.kind, amountCents = cents, categoryId = cat.id, date = date, createdAt = now, updatedAt = now)
        val facts = askFacts(
            emptyList(), emptyMap(), null, today,
            categories = listOf(food, salary),
            movements = listOf(
                m(100_00, food, LocalDate.of(2026, 10, 27)),
                m(50_00, food, LocalDate.of(2026, 10, 20)), // last week: not this week
                m(300_00, food, LocalDate.of(2026, 9, 10)),
                m(1_000_00, salary, LocalDate.of(2026, 9, 1)),
            ),
        )
        assertTrue(facts, facts.contains("Gastos de esta semana (desde el lunes 26 de octubre de 2026): Comida \$100.00. Total: \$100.00."))
        assertTrue(facts, facts.contains("Mes pasado (septiembre 2026): entró \$1,000.00, salió \$300.00. Gastos por categoría: Comida \$300.00."))
    }
}
