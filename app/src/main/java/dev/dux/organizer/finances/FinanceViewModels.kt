package dev.dux.organizer.finances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.data.Category
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.DebtPayment
import dev.dux.organizer.data.DebtWithPaid
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class FinancesState(
    val month: YearMonth,
    val categories: List<Category>,
    val movements: List<Movement>,
    val summary: MonthSummary,
    val debts: List<DebtWithPaid>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinancesViewModel(private val repo: FinanceRepository, initialMonth: YearMonth = YearMonth.now()) : ViewModel() {
    private val month = MutableStateFlow(initialMonth)

    /** null = still loading. */
    val state: StateFlow<FinancesState?> = combine(month, repo.categories, month.flatMapLatest { repo.movements(it) }, repo.debts) { m, categories, movements, debts ->
        FinancesState(m, categories, movements, monthSummary(categories, movements), debts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = month.value.minusMonths(1).let { month.value = it }
    fun nextMonth() = month.value.plusMonths(1).let { month.value = it }

    /** [id] null = new movement. */
    fun saveMovement(id: String?, amountCents: Long, categoryId: String, date: LocalDate, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(if (id == null) repo.addMovement(amountCents, categoryId, date, note) else repo.updateMovement(id, amountCents, categoryId, date, note))
        }
    }

    fun deleteMovement(id: String) {
        viewModelScope.launch { repo.deleteMovement(id) }
    }

    /** [id] null = new category of [kind]. */
    fun saveCategory(id: String?, name: String, kind: MoneyKind, budgetCents: Long?, archived: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(if (id == null) repo.createCategory(name, kind, budgetCents) else repo.updateCategory(id, name, budgetCents, archived))
        }
    }

    fun addDebt(person: String, direction: DebtDirection, amountCents: Long, dueDate: LocalDate?, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.addDebt(person, direction, amountCents, dueDate, note)) }
    }
}

class DebtDetailViewModel(private val id: String, private val repo: FinanceRepository) : ViewModel() {
    /** Also emits null if the debt was deleted. */
    val debt: StateFlow<DebtWithPaid?> = repo.debt(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val payments: StateFlow<List<DebtPayment>?> = repo.payments(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(person: String, amountCents: Long, dueDate: LocalDate?, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.updateDebt(id, person, amountCents, dueDate, note)) }
    }

    fun addPayment(amountCents: Long, date: LocalDate, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.addPayment(id, amountCents, date)) }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch { repo.deletePayment(paymentId) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repo.deleteDebt(id); onDone() }
    }
}
