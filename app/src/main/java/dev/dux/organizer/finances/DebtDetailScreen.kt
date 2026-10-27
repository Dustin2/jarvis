package dev.dux.organizer.finances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dux.organizer.R
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.ui.SavedIndicator
import dev.dux.organizer.ui.asHeading
import dev.dux.organizer.ui.label
import dev.dux.organizer.ui.rememberToday

@Composable
fun DebtDetailScreen(vm: DebtDetailViewModel, onBack: () -> Unit) {
    val item by vm.debt.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val today by rememberToday()
    var editing by rememberSaveable { mutableStateOf(false) }
    var paying by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deletingPayment by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SavedIndicator(item?.debt?.updatedAt) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
                actions = {
                    if (item != null) {
                        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_debt)) }
                    }
                },
            )
        },
    ) { padding ->
        val current = item ?: return@Scaffold
        val list = payments ?: return@Scaffold
        val debt = current.debt
        val settled = debt.settledAt != null
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(debt.direction.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(debt.person, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.asHeading())
                    Text(
                        if (settled) stringResource(R.string.debt_settled_amount, formatMoney(debt.amountCents))
                        else stringResource(R.string.remaining_of, formatMoney(current.remainingCents), formatMoney(debt.amountCents)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LinearProgressIndicator(progress = { current.paidCents.toFloat() / debt.amountCents }, modifier = Modifier.fillMaxWidth())
                    debt.dueDate?.let { due ->
                        val overdue = !settled && due < today
                        Text(
                            stringResource(R.string.due_label, due.label(today)),
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    debt.note?.let { Text(it) }
                    if (!settled) Button(onClick = { paying = true }) { Text(stringResource(R.string.add_payment)) }
                }
            }
            item {
                Text(
                    stringResource(R.string.payments),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).asHeading(),
                )
            }
            if (list.isEmpty()) {
                item { Text(stringResource(R.string.empty_payments), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp)) }
            }
            items(list, key = { it.id }) { payment ->
                ListItem(
                    supportingContent = { Text(payment.date.label(today)) },
                    trailingContent = {
                        IconButton(onClick = { deletingPayment = payment.id }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_payment, formatMoney(payment.amountCents)))
                        }
                    },
                ) { Text(formatMoney(payment.amountCents)) }
            }
        }

        if (editing) {
            DebtSheet(
                initial = debt,
                onDismiss = { editing = false },
                onSave = { person, _, cents, due, note, done -> vm.update(person, cents, due, note) { ok -> done(ok); if (ok) editing = false } },
            )
        }
        if (paying) {
            PaymentDialog(current.remainingCents, onDismiss = { paying = false }) { cents, date, done ->
                vm.addPayment(cents, date) { ok -> done(ok); if (ok) paying = false }
            }
        }
        deletingPayment?.let { id ->
            ConfirmDelete(R.string.delete_payment_confirm, onDismiss = { deletingPayment = null }) { vm.deletePayment(id) }
        }
        if (confirmDelete) {
            ConfirmDelete(R.string.delete_debt_confirm, onDismiss = { confirmDelete = false }) { vm.delete(onBack) }
        }
    }
}
