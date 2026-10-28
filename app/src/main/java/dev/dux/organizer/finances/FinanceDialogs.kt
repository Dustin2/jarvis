package dev.dux.organizer.finances

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.data.Category
import dev.dux.organizer.data.Debt
import dev.dux.organizer.data.DebtDirection
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Movement
import dev.dux.organizer.data.cleanTitle
import dev.dux.organizer.data.editableMoney
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.data.parseMoney
import dev.dux.organizer.ui.Choice
import dev.dux.organizer.ui.ChoiceChips
import dev.dux.organizer.ui.DatePickDialog
import dev.dux.organizer.ui.Drafts
import dev.dux.organizer.ui.PlanRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday
import java.time.LocalDate

@get:StringRes
val MoneyKind.label: Int
    get() = when (this) {
        MoneyKind.EXPENSE -> R.string.kind_expense
        MoneyKind.INCOME -> R.string.kind_income
    }

@get:StringRes
val DebtDirection.label: Int
    get() = when (this) {
        DebtDirection.I_OWE -> R.string.debt_i_owe
        DebtDirection.OWED_TO_ME -> R.string.debt_owed_to_me
    }

/** Decimal keyboard and a "$" prefix; the text is parsed with [parseMoney] by whoever saves. */
@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, @StringRes label: Int, error: String?, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        prefix = { Text("$") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        // "Done" hides the keyboard, uncovering the category chips and the save button.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Date button that opens the picker. With [canRemove], the picker offers "Quitar fecha". */
@Composable
private fun DateButton(@StringRes title: Int, date: LocalDate?, canRemove: Boolean, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today by rememberToday()
    // Same row as the task detail (docs/design/task-detail.md).
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        PlanRow(R.drawable.ic_today, title, date?.label(today) ?: stringResource(R.string.no_date), isSet = date != null, onClick = { picking = true })
    }
    if (picking) {
        DatePickDialog(date ?: today, if (canRemove) R.string.remove_date else R.string.cancel, canRemove = true, onDismiss = { picking = false }) { picked ->
            picking = false
            // Without canRemove the second button is "Cancelar": null means no change.
            if (picked != null || canRemove) onChange(picked)
        }
    }
}

/**
 * Registers or edits a movement: amount first (numeric keyboard), then the category, date defaults to today.
 * [onSave] receives cents, category id, date, note and a callback saying whether it was saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementSheet(
    categories: List<Category>,
    initial: Movement?,
    drafts: Drafts,
    onDismiss: () -> Unit,
    onSave: (Long, String, LocalDate, String, (Boolean) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val today by rememberToday()
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: MoneyKind.EXPENSE) }
    // A new movement keeps its typed text in the screen's drafts; editing starts from the saved one.
    var amount by if (initial == null) drafts.field("movement.amount") else rememberSaveable { mutableStateOf(editableMoney(initial.amountCents)) }
    var categoryId by rememberSaveable { mutableStateOf(initial?.categoryId) }
    var date by rememberSaveable { mutableStateOf(initial?.date ?: today) }
    var note by if (initial == null) drafts.field("movement.note") else rememberSaveable { mutableStateOf(initial.note ?: "") }
    var amountError by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // Archived categories are hidden, except the one this movement already uses.
    val options = categories.filter { it.kind == kind && (!it.archived || it.id == initial?.categoryId) }
    val category = options.firstOrNull { it.id == categoryId } ?: options.firstOrNull()
    val save = {
        val cents = parseMoney(amount)
        if (cents == null) amountError = true
        else if (category != null) onSave(cents, category.id, date, note) { ok -> amountError = !ok }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            // Scrolls: in landscape or with large fonts the keyboard covers the lower fields.
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(if (initial == null) R.string.register_movement else R.string.edit_movement), style = MaterialTheme.typography.headlineSmall)
            Choice(R.string.kind, MoneyKind.entries, kind, { it.label }) { kind = it }
            MoneyField(
                amount,
                { amount = it; amountError = false },
                R.string.amount,
                if (amountError) stringResource(R.string.amount_invalid) else null,
                Modifier.focusRequester(focus),
            )
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(options, category, { it.name }) { categoryId = it.id }
            DateButton(R.string.date, date, canRemove = false) { if (it != null) date = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                Button(onClick = save, enabled = category != null) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (initial == null) LaunchedEffect(Unit) { focus.requestFocus() }
    if (confirmDelete && onDelete != null) {
        ConfirmDelete(R.string.delete_movement_confirm, onDismiss = { confirmDelete = false }, onConfirm = onDelete)
    }
}

@Composable
fun ConfirmDelete(@StringRes title: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(R.string.delete_confirm_body)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** New category (the kind is chosen here) or edit (name, budget, archived). */
@Composable
fun CategoryDialog(initial: Category?, onDismiss: () -> Unit, onSave: (String, MoneyKind, Long?, Boolean, (Boolean) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: MoneyKind.EXPENSE) }
    var budget by rememberSaveable { mutableStateOf(initial?.monthlyBudgetCents?.let(::editableMoney) ?: "") }
    var archived by rememberSaveable { mutableStateOf(initial?.archived ?: false) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var budgetError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_category else R.string.edit_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text(stringResource(R.string.title_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (initial == null) Choice(R.string.kind, MoneyKind.entries, kind, { it.label }) { kind = it }
                if (kind == MoneyKind.EXPENSE) {
                    MoneyField(budget, { budget = it; budgetError = false }, R.string.monthly_budget_optional, if (budgetError) stringResource(R.string.amount_invalid) else null)
                }
                if (initial != null) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(archived, role = Role.Checkbox) { archived = it },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(archived, onCheckedChange = null)
                        Text(stringResource(R.string.archive_category), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = if (kind == MoneyKind.EXPENSE && budget.isNotBlank()) parseMoney(budget) else null
                budgetError = kind == MoneyKind.EXPENSE && budget.isNotBlank() && cents == null
                if (!budgetError) onSave(name, kind, cents, archived) { ok -> nameError = !ok }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** New debt (the direction is chosen here) or edit. [onSave]: person, direction, cents, due date, note, result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSheet(
    initial: Debt?,
    onDismiss: () -> Unit,
    onSave: (String, DebtDirection, Long, LocalDate?, String, (Boolean) -> Unit) -> Unit,
    drafts: Drafts? = null,
) {
    var direction by rememberSaveable { mutableStateOf(initial?.direction ?: DebtDirection.I_OWE) }
    // A new debt keeps its typed text in the screen's drafts; editing starts from the saved one.
    val personState: MutableState<String> = drafts?.field("debt.person") ?: rememberSaveable { mutableStateOf(initial?.person ?: "") }
    val amountState: MutableState<String> = drafts?.field("debt.amount") ?: rememberSaveable { mutableStateOf(initial?.let { editableMoney(it.amountCents) } ?: "") }
    val noteState: MutableState<String> = drafts?.field("debt.note") ?: rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var person by personState
    var amount by amountState
    var note by noteState
    var dueDate by rememberSaveable { mutableStateOf(initial?.dueDate) }
    var personError by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf(false) }
    val save = {
        val cents = parseMoney(amount)
        amountError = cents == null
        if (cents != null) onSave(person, direction, cents, dueDate, note) { ok ->
            // Rejected: either the name, or (when editing) an amount below what was already paid.
            if (!ok) { personError = cleanTitle(person) == null; amountError = !personError }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(if (initial == null) R.string.new_debt else R.string.edit_debt), style = MaterialTheme.typography.headlineSmall)
            if (initial == null) Choice(R.string.debt_direction, DebtDirection.entries, direction, { it.label }) { direction = it }
            OutlinedTextField(
                value = person,
                onValueChange = { person = it; personError = false },
                label = { Text(stringResource(R.string.person)) },
                isError = personError,
                supportingText = if (personError) ({ Text(stringResource(R.string.title_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                amount,
                { amount = it; amountError = false },
                R.string.amount,
                if (amountError) stringResource(if (initial == null) R.string.amount_invalid else R.string.debt_amount_invalid) else null,
            )
            DateButton(R.string.due_date, dueDate, canRemove = true) { dueDate = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = save, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.save)) }
        }
    }
}

/** [onSave] receives cents, date and a callback saying whether it was saved (false = more than what is left). */
@Composable
fun PaymentDialog(remainingCents: Long, onDismiss: () -> Unit, onSave: (Long, LocalDate, (Boolean) -> Unit) -> Unit) {
    val today by rememberToday()
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today) }
    var error by rememberSaveable { mutableStateOf(false) }
    val remaining = formatMoney(remainingCents)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(amount, { amount = it; error = false }, R.string.amount, if (error) stringResource(R.string.payment_invalid, remaining) else null)
                // One tap to pay it off.
                AssistChip(onClick = { amount = editableMoney(remainingCents); error = false }, label = { Text(stringResource(R.string.pay_all, remaining)) })
                DateButton(R.string.date, date, canRemove = false) { if (it != null) date = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseMoney(amount)
                if (cents == null || cents > remainingCents) error = true else onSave(cents, date) { ok -> error = !ok }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
