package dev.dux.organizer.finances

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement

data class CategoryLine(val category: Category, val spentCents: Long) {
    /** How much over the budget; null if there is no budget or it was not exceeded. */
    val overCents: Long? get() = category.monthlyBudgetCents?.let { spentCents - it }?.takeIf { it > 0 }
}

data class MonthSummary(
    val incomeCents: Long,
    val expenseCents: Long,
    /** Active expense categories, plus archived ones that still have spending this month. */
    val lines: List<CategoryLine>,
    /** Sum of the active budgets; null if no category has one. */
    val budgetCents: Long?,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

/** [movements] must be the month's; [categories] in display order. */
fun monthSummary(categories: List<Category>, movements: List<Movement>): MonthSummary {
    val spent = movements.filter { it.kind == MoneyKind.EXPENSE }.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
    val expenseCategories = categories.filter { it.kind == MoneyKind.EXPENSE }
    return MonthSummary(
        incomeCents = movements.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amountCents },
        expenseCents = spent.values.sum(),
        lines = expenseCategories.filter { !it.archived || it.id in spent }.map { CategoryLine(it, spent[it.id] ?: 0) },
        budgetCents = expenseCategories.filter { !it.archived }.mapNotNull { it.monthlyBudgetCents }.takeIf { it.isNotEmpty() }?.sum(),
    )
}
