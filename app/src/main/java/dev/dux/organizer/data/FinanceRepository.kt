package dev.dux.organizer.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Amounts arrive already parsed ([parseMoney]); anything out of range is rejected here too. */
class FinanceRepository(private val dao: FinanceDao, private val clock: Clock = Clock.systemUTC()) {

    val categories: Flow<List<Category>> = dao.observeCategories()
    val debts: Flow<List<DebtWithPaid>> = dao.observeDebts()

    val allMovements: Flow<List<Movement>> = dao.observeAllMovements()

    fun movements(month: YearMonth): Flow<List<Movement>> = dao.observeMovements(month.atDay(1), month.atEndOfMonth())
    fun debt(id: String): Flow<DebtWithPaid?> = dao.observeDebt(id)
    fun payments(debtId: String): Flow<List<DebtPayment>> = dao.observePayments(debtId)

    // "Empty table" = first run: holds because categories are archived, never deleted.
    /** Names in the phone's language, from resources (JarvisApplication). */
    suspend fun seedIfEmpty(expense: List<String> = INITIAL_EXPENSE_CATEGORIES, income: List<String> = INITIAL_INCOME_CATEGORIES) {
        val now = clock.instant()
        fun seed(names: List<String>, kind: MoneyKind) =
            names.mapIndexed { i, name -> Category(name = name, kind = kind, sortOrder = i, createdAt = now, updatedAt = now) }
        dao.seedIfEmpty(seed(expense, MoneyKind.EXPENSE) + seed(income, MoneyKind.INCOME))
    }

    suspend fun createCategory(name: String, kind: MoneyKind, budgetCents: Long?): Boolean {
        val clean = cleanTitle(name) ?: return false
        if (!validBudget(kind, budgetCents)) return false
        val now = clock.instant()
        dao.insertCategory(
            Category(name = clean, kind = kind, monthlyBudgetCents = budgetCents, sortOrder = dao.nextCategorySortOrder(kind), createdAt = now, updatedAt = now)
        )
        return true
    }

    suspend fun updateCategory(id: String, name: String, budgetCents: Long?, archived: Boolean): Boolean {
        val clean = cleanTitle(name) ?: return false
        val current = dao.getCategory(id) ?: return false
        if (!validBudget(current.kind, budgetCents)) return false
        val changed = current.copy(name = clean, monthlyBudgetCents = budgetCents, archived = archived)
        if (changed != current) dao.updateCategory(changed.copy(updatedAt = clock.instant()))
        return true
    }

    /** The kind comes from the category, so an expense can never land in an income category. */
    suspend fun addMovement(amountCents: Long, categoryId: String, date: LocalDate, note: String = "", id: String = UUID.randomUUID().toString()): Boolean {
        if (!validAmount(amountCents)) return false
        val category = dao.getCategory(categoryId) ?: return false
        val now = clock.instant()
        dao.insertMovement(
            Movement(id = id, kind = category.kind, amountCents = amountCents, categoryId = categoryId, date = date, note = cleanNote(note), createdAt = now, updatedAt = now)
        )
        return true
    }

    suspend fun updateMovement(id: String, amountCents: Long, categoryId: String, date: LocalDate, note: String): Boolean {
        if (!validAmount(amountCents)) return false
        val current = dao.getMovement(id) ?: return false
        val category = dao.getCategory(categoryId) ?: return false
        val changed = current.copy(kind = category.kind, amountCents = amountCents, categoryId = categoryId, date = date, note = cleanNote(note))
        if (changed != current) dao.updateMovement(changed.copy(updatedAt = clock.instant()))
        return true
    }

    /** Returns what was deleted, for "Deshacer". */
    suspend fun deleteMovement(id: String): Movement? = dao.getMovement(id)?.also { dao.deleteMovement(id) }

    suspend fun restoreMovement(movement: Movement) = dao.insertMovement(movement)

    suspend fun addDebt(person: String, direction: DebtDirection, amountCents: Long, dueDate: LocalDate?, note: String = ""): Boolean {
        val clean = cleanTitle(person) ?: return false
        if (!validAmount(amountCents)) return false
        val now = clock.instant()
        dao.insertDebt(
            Debt(person = clean, direction = direction, amountCents = amountCents, dueDate = dueDate, note = cleanNote(note), createdAt = now, updatedAt = now)
        )
        return true
    }

    /** Returns false if a field is not valid or the amount is below what was already paid. */
    suspend fun updateDebt(id: String, person: String, amountCents: Long, dueDate: LocalDate?, note: String): Boolean {
        val clean = cleanTitle(person) ?: return false
        if (!validAmount(amountCents)) return false
        val current = dao.getDebt(id) ?: return false
        val changed = current.copy(person = clean, amountCents = amountCents, dueDate = dueDate, note = cleanNote(note))
        return changed == current || dao.updateDebtChecked(changed, clock.instant())
    }

    /** Returns the debt and its payments, for "Deshacer". */
    suspend fun deleteDebt(id: String): Pair<Debt, List<DebtPayment>>? = dao.takeDebt(id)

    suspend fun restoreDebt(debt: Debt, payments: List<DebtPayment>) = dao.putDebt(debt, payments)

    /** Returns false if the amount is not valid or is larger than what is left. */
    suspend fun addPayment(debtId: String, amountCents: Long, date: LocalDate): Boolean {
        if (!validAmount(amountCents)) return false
        val now = clock.instant()
        return dao.addPayment(DebtPayment(debtId = debtId, amountCents = amountCents, date = date, createdAt = now), now)
    }

    suspend fun deletePayment(id: String): Pair<DebtPayment, Debt>? = dao.takePayment(id, clock.instant())

    suspend fun restorePayment(payment: DebtPayment, before: Debt) = dao.putPayment(payment, before)

    private fun validAmount(cents: Long) = cents in 1..MAX_AMOUNT_CENTS
    private fun validBudget(kind: MoneyKind, cents: Long?) = cents == null || (kind == MoneyKind.EXPENSE && validAmount(cents))
    private fun cleanNote(note: String) = note.trim().ifEmpty { null }

    companion object {
        val INITIAL_EXPENSE_CATEGORIES = listOf("Comida", "Transporte", "Casa", "Servicios", "Salud", "Entretenimiento", "Otros")
        val INITIAL_INCOME_CATEGORIES = listOf("Sueldo", "Otros ingresos")
    }
}
