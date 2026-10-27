package dev.dux.organizer.finances

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MonthSummaryTest {
    private val t = Instant.EPOCH
    private fun category(id: String, kind: MoneyKind = MoneyKind.EXPENSE, budget: Long? = null, archived: Boolean = false) =
        Category(id = id, name = id, kind = kind, monthlyBudgetCents = budget, archived = archived, createdAt = t, updatedAt = t)
    private fun movement(categoryId: String, cents: Long, kind: MoneyKind = MoneyKind.EXPENSE) =
        Movement(kind = kind, amountCents = cents, categoryId = categoryId, date = LocalDate.of(2026, 10, 27), createdAt = t, updatedAt = t)

    @Test fun totalsLinesAndBudget() {
        val categories = listOf(
            category("comida", budget = 300_000), category("transporte", budget = 50_000), category("otros"),
            category("viejo", archived = true, budget = 99_900), category("antiguo", archived = true),
            category("sueldo", MoneyKind.INCOME),
        )
        val movements = listOf(
            movement("comida", 120_000), movement("comida", 30_000), movement("transporte", 73_000),
            movement("viejo", 10_000), movement("sueldo", 1_500_000, MoneyKind.INCOME),
        )
        val summary = monthSummary(categories, movements)

        assertEquals(1_500_000L, summary.incomeCents)
        assertEquals(233_000L, summary.expenseCents)
        assertEquals(1_267_000L, summary.balanceCents)
        // Archived only while it has spending this month; income categories never appear.
        assertEquals(listOf("comida", "transporte", "otros", "viejo"), summary.lines.map { it.category.id })
        assertEquals(listOf(150_000L, 73_000L, 0L, 10_000L), summary.lines.map { it.spentCents })
        assertEquals(listOf(null, 23_000L, null, null), summary.lines.map { it.overCents })
        // Archived budgets do not count.
        assertEquals(350_000L, summary.budgetCents)
    }

    @Test fun emptyMonthWithoutBudgets() {
        val summary = monthSummary(listOf(category("comida")), emptyList())
        assertEquals(0L, summary.balanceCents)
        assertNull(summary.budgetCents)
    }
}
