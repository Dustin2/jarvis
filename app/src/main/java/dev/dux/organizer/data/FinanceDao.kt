package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_categories ORDER BY kind, sortOrder, name")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM finance_movements WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeMovements(from: LocalDate, to: LocalDate): Flow<List<Movement>>

    @Query("SELECT * FROM finance_movements")
    fun observeAllMovements(): Flow<List<Movement>>

    @Query(
        "SELECT d.*, COALESCE((SELECT SUM(p.amountCents) FROM finance_debt_payments p WHERE p.debtId = d.id), 0) AS paidCents " +
            "FROM finance_debts d ORDER BY d.settledAt IS NOT NULL, d.dueDate IS NULL, d.dueDate, d.createdAt"
    )
    fun observeDebts(): Flow<List<DebtWithPaid>>

    @Query(
        "SELECT d.*, COALESCE((SELECT SUM(p.amountCents) FROM finance_debt_payments p WHERE p.debtId = d.id), 0) AS paidCents " +
            "FROM finance_debts d WHERE d.id = :id"
    )
    fun observeDebt(id: String): Flow<DebtWithPaid?>

    @Query("SELECT * FROM finance_debt_payments WHERE debtId = :debtId ORDER BY date DESC, createdAt DESC")
    fun observePayments(debtId: String): Flow<List<DebtPayment>>

    @Query("SELECT * FROM finance_categories WHERE id = :id") suspend fun getCategory(id: String): Category?
    @Query("SELECT * FROM finance_movements WHERE id = :id") suspend fun getMovement(id: String): Movement?
    @Query("SELECT * FROM finance_debts WHERE id = :id") suspend fun getDebt(id: String): Debt?
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM finance_debt_payments WHERE debtId = :debtId")
    suspend fun paidCents(debtId: String): Long

    @Query("SELECT * FROM finance_categories ORDER BY kind, sortOrder") suspend fun getAllCategories(): List<Category>
    @Query("SELECT * FROM finance_movements ORDER BY date, createdAt") suspend fun getAllMovements(): List<Movement>
    @Query("SELECT * FROM finance_debts ORDER BY createdAt") suspend fun getAllDebts(): List<Debt>
    @Query("SELECT * FROM finance_debt_payments ORDER BY debtId, date, createdAt") suspend fun getAllPayments(): List<DebtPayment>

    @Query("SELECT COUNT(*) FROM finance_categories") suspend fun countCategories(): Int
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM finance_categories WHERE kind = :kind")
    suspend fun nextCategorySortOrder(kind: MoneyKind): Int

    @Insert suspend fun insertCategory(category: Category)
    @Insert suspend fun insertCategories(categories: List<Category>)
    @Insert suspend fun insertMovement(movement: Movement)
    @Insert suspend fun insertMovements(movements: List<Movement>)
    @Insert suspend fun insertDebt(debt: Debt)
    @Insert suspend fun insertDebts(debts: List<Debt>)
    @Insert suspend fun insertPayment(payment: DebtPayment)
    @Insert suspend fun insertPayments(payments: List<DebtPayment>)
    @Update suspend fun updateCategory(category: Category)
    @Update suspend fun updateMovement(movement: Movement)
    @Update suspend fun updateDebt(debt: Debt)

    @Query("DELETE FROM finance_movements WHERE id = :id") suspend fun deleteMovement(id: String)
    @Query("DELETE FROM finance_debts WHERE id = :id") suspend fun deleteDebt(id: String)
    @Query("DELETE FROM finance_debt_payments WHERE id = :id") suspend fun deletePayment(id: String)

    @Query("SELECT * FROM finance_debt_payments WHERE id = :id") suspend fun getPayment(id: String): DebtPayment?

    /** Rejects a payment larger than what is left; settles the debt when it is paid off. */
    @Transaction
    suspend fun addPayment(payment: DebtPayment, now: Instant): Boolean {
        val debt = getDebt(payment.debtId) ?: return false
        val remaining = debt.amountCents - paidCents(debt.id)
        if (payment.amountCents > remaining) return false
        insertPayment(payment)
        if (payment.amountCents == remaining) updateDebt(debt.copy(settledAt = now, updatedAt = now))
        return true
    }

    @Transaction
    suspend fun removePayment(id: String, now: Instant) {
        val payment = getPayment(id) ?: return
        deletePayment(id)
        val debt = getDebt(payment.debtId) ?: return
        if (debt.settledAt != null) updateDebt(debt.copy(settledAt = null, updatedAt = now))
    }

    /** Rejects an amount below what was already paid; keeps [Debt.settledAt] consistent with the payments. */
    @Transaction
    suspend fun updateDebtChecked(changed: Debt, now: Instant): Boolean {
        val paid = paidCents(changed.id)
        if (changed.amountCents < paid) return false
        val settledAt = if (changed.amountCents == paid) changed.settledAt ?: now else null
        updateDebt(changed.copy(settledAt = settledAt, updatedAt = now))
        return true
    }

    @Transaction
    suspend fun seedIfEmpty(categories: List<Category>) {
        if (countCategories() == 0) insertCategories(categories)
    }

    /** Clears everything for a restore. Children first: movements point to categories. */
    @Transaction
    suspend fun deleteAll() {
        deleteAllMovements()
        deleteAllCategories()
        deleteAllDebts() // payments go with them (ON DELETE CASCADE)
    }

    @Query("DELETE FROM finance_movements") suspend fun deleteAllMovements()
    @Query("DELETE FROM finance_categories") suspend fun deleteAllCategories()
    @Query("DELETE FROM finance_debts") suspend fun deleteAllDebts()
}
