package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val finance = FinanceRepository(db.finance())
    private val october = YearMonth.of(2026, 10)

    @After fun tearDown() = db.close()

    private suspend fun category(name: String) = finance.categories.first().first { it.name == name }
    private suspend fun debt() = finance.debts.first().single()

    @Test fun seedsCategoriesOnlyOnce() = runBlocking {
        finance.seedIfEmpty()
        finance.seedIfEmpty()
        val categories = finance.categories.first()
        assertEquals(FinanceRepository.INITIAL_EXPENSE_CATEGORIES, categories.filter { it.kind == MoneyKind.EXPENSE }.map { it.name })
        assertEquals(FinanceRepository.INITIAL_INCOME_CATEGORIES, categories.filter { it.kind == MoneyKind.INCOME }.map { it.name })
    }

    @Test fun movementsTakeTheKindOfTheirCategoryAndFilterByMonth() = runBlocking {
        finance.seedIfEmpty()
        val food = category("Comida").id
        val salary = category("Sueldo").id
        assertTrue(finance.addMovement(8550, food, LocalDate.of(2026, 10, 27), " tacos "))
        assertTrue(finance.addMovement(1_500_000, salary, LocalDate.of(2026, 10, 15)))
        assertTrue(finance.addMovement(10_000, food, LocalDate.of(2026, 11, 1)))
        assertFalse(finance.addMovement(0, food, LocalDate.of(2026, 10, 27)))
        assertFalse(finance.addMovement(100, "no-existe", LocalDate.of(2026, 10, 27)))

        val list = finance.movements(october).first()
        assertEquals(listOf(8550L, 1_500_000L), list.map { it.amountCents })
        assertEquals(listOf(MoneyKind.EXPENSE, MoneyKind.INCOME), list.map { it.kind })
        assertEquals("tacos", list[0].note)

        // Moving it to an income category changes its kind too.
        assertTrue(finance.updateMovement(list[0].id, 9000, salary, LocalDate.of(2026, 10, 27), ""))
        val updated = finance.movements(october).first().first { it.amountCents == 9000L }
        assertEquals(MoneyKind.INCOME, updated.kind)
        assertNull(updated.note)
    }

    @Test fun budgetsOnlyForExpenseCategoriesAndArchiving() = runBlocking {
        assertTrue(finance.createCategory("Gimnasio", MoneyKind.EXPENSE, 50_000))
        assertFalse(finance.createCategory("Bonos", MoneyKind.INCOME, 50_000))
        assertFalse(finance.createCategory(" ", MoneyKind.EXPENSE, null))
        val gym = category("Gimnasio")
        assertTrue(finance.updateCategory(gym.id, "Gym", null, archived = true))
        val updated = category("Gym")
        assertNull(updated.monthlyBudgetCents)
        assertTrue(updated.archived)
    }

    @Test fun paymentsSettleAndUnsettleTheDebt() = runBlocking {
        assertTrue(finance.addDebt("Juan", DebtDirection.OWED_TO_ME, 100_000, LocalDate.of(2026, 11, 30)))
        val id = debt().debt.id
        assertTrue(finance.addPayment(id, 40_000, LocalDate.of(2026, 10, 27)))
        assertFalse("more than what is left", finance.addPayment(id, 60_001, LocalDate.of(2026, 10, 27)))
        assertEquals(60_000L, debt().remainingCents)
        assertNull(debt().debt.settledAt)

        assertTrue(finance.addPayment(id, 60_000, LocalDate.of(2026, 10, 28)))
        assertEquals(0L, debt().remainingCents)
        assertNotNull(debt().debt.settledAt)

        val last = finance.payments(id).first().first()
        finance.deletePayment(last.id)
        assertEquals(60_000L, debt().remainingCents)
        assertNull(debt().debt.settledAt)
    }

    @Test fun editingTheAmountRespectsWhatWasPaid() = runBlocking {
        finance.addDebt("Banco", DebtDirection.I_OWE, 100_000, null)
        val id = debt().debt.id
        finance.addPayment(id, 40_000, LocalDate.of(2026, 10, 27))
        assertFalse(finance.updateDebt(id, "Banco", 39_999, null, ""))
        assertTrue(finance.updateDebt(id, "Banco", 40_000, null, ""))
        assertNotNull("lowering it to what was paid settles it", debt().debt.settledAt)
        assertTrue(finance.updateDebt(id, "Banco", 50_000, null, ""))
        assertNull(debt().debt.settledAt)
    }

    @Test fun deletingADebtDeletesItsPayments() = runBlocking {
        finance.addDebt("Ana", DebtDirection.I_OWE, 10_000, null)
        val id = debt().debt.id
        finance.addPayment(id, 1_000, LocalDate.of(2026, 10, 27))
        finance.deleteDebt(id)
        assertTrue(db.finance().getAllPayments().isEmpty())
    }

    @Test fun undoPutsBackADeletedMovementAsItWas() = runBlocking {
        finance.seedIfEmpty()
        finance.addMovement(2_500, category("Comida").id, LocalDate.of(2026, 10, 5), "tacos")
        val before = db.finance().getAllMovements().single()
        val gone = finance.deleteMovement(before.id)!!
        assertTrue(db.finance().getAllMovements().isEmpty())
        finance.restoreMovement(gone)
        assertEquals(before, db.finance().getAllMovements().single())
    }

    @Test fun undoPutsBackADeletedDebtWithItsPayments() = runBlocking {
        finance.addDebt("Ana", DebtDirection.I_OWE, 10_000, null)
        val id = debt().debt.id
        finance.addPayment(id, 4_000, LocalDate.of(2026, 10, 27))
        finance.addPayment(id, 6_000, LocalDate.of(2026, 10, 28)) // pays it off
        val before = debt()
        val payments = db.finance().getAllPayments()
        val (gone, goneP) = finance.deleteDebt(id)!!
        finance.restoreDebt(gone, goneP)
        assertEquals(before, debt())
        assertEquals(payments, db.finance().getAllPayments())
    }

    @Test fun undoPutsBackADeletedPaymentAndSettlesTheDebtAgain() = runBlocking {
        finance.addDebt("Luis", DebtDirection.OWED_TO_ME, 5_000, null)
        val id = debt().debt.id
        finance.addPayment(id, 5_000, LocalDate.of(2026, 10, 27)) // pays it off
        val before = debt()
        val (gone, debtBefore) = finance.deletePayment(finance.payments(id).first().single().id)!!
        assertNull(debt().debt.settledAt)
        finance.restorePayment(gone, debtBefore)
        assertEquals(before, debt())
        assertEquals(listOf(gone), db.finance().getAllPayments())
    }
}
