package dev.dux.organizer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Design: docs/design/finances.md. Amounts are always positive cents; the kind says the direction.

enum class MoneyKind { EXPENSE, INCOME }

/** Never deleted, only archived: past movements keep pointing to it. */
@Entity(tableName = "finance_categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: MoneyKind,
    /** Expense categories only; the same every month. */
    val monthlyBudgetCents: Long? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "finance_movements",
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"])],
    indices = [Index("categoryId"), Index("date")],
)
data class Movement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: MoneyKind,
    val amountCents: Long,
    val categoryId: String,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class DebtDirection { I_OWE, OWED_TO_ME }

@Entity(tableName = "finance_debts")
data class Debt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val person: String,
    val direction: DebtDirection,
    val amountCents: Long,
    val dueDate: LocalDate? = null,
    val note: String? = null,
    /** Set when the payments cover the amount. */
    val settledAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "finance_debt_payments",
    foreignKeys = [ForeignKey(entity = Debt::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId")],
)
data class DebtPayment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val debtId: String,
    val amountCents: Long,
    val date: LocalDate,
    val createdAt: Instant,
)

/** What is left is always computed from the payments, so it can never drift. */
data class DebtWithPaid(@Embedded val debt: Debt, val paidCents: Long) {
    val remainingCents: Long get() = debt.amountCents - paidCents
}
