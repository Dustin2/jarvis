package dev.dux.organizer.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.ask.splitItems
import dev.dux.organizer.ui.asHeading

/**
 * Adds items to [listName] one after another, like capturing tasks: the sheet stays open after each one and
 * shows what went in. "leche, pan y huevos" adds three; the quantity, when given, goes with a single item.
 */
// ModalBottomSheet has been marked experimental for years, but it is the official M3 pattern for this.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddItemsSheet(
    listName: String,
    onAdd: (List<String>, String, (String, Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    suggest: (String) -> List<String> = { emptyList() },
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var withQuantity by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }

    /** Adds [names] and leaves [rest] in the field. */
    fun addNames(names: List<String>, rest: String = "") {
        // A quantity only makes sense for one item.
        val qty = if (names.size == 1) quantity else ""
        if (names.isEmpty()) error = true
        if (names.isNotEmpty()) {
            onAdd(names, qty) { item, ok -> if (ok) added += item else error = true }
            name = rest
            quantity = ""
            withQuantity = false
        }
        focus.requestFocus()
    }
    val add: () -> Unit = { addNames(splitItems(name)) }
    // Suggestions follow what is being written after the last comma: "pan, le" suggests "Leche".
    val typing = name.substringAfterLast(',')
    val suggestions = suggest(typing)
    val addLabel = stringResource(R.string.add)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Open the keyboard right away (once the field is in the sheet): adding should take seconds.
            LaunchedEffect(Unit) { focus.requestFocus() }
            Text(stringResource(R.string.add_to_list, listName), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
            TextField(
                value = name,
                onValueChange = { name = it; error = false },
                label = { Text(stringResource(R.string.add_item_hint)) },
                supportingText = { Text(stringResource(if (error) R.string.item_invalid else R.string.add_items_tip)) },
                isError = error,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { addNames(listOf(suggestion), name.dropLast(typing.length)) },
                            label = { Text(suggestion) },
                            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp)) },
                            modifier = Modifier.semantics { contentDescription = "$addLabel $suggestion" },
                        )
                    }
                }
            }
            if (withQuantity) {
                TextField(
                    value = quantity,
                    onValueChange = { quantity = it; error = false },
                    label = { Text(stringResource(R.string.quantity_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (added.isNotEmpty()) {
                // What went in this time; TalkBack announces each one, since the field clears right away.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    added.forEach { item ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(16.dp))
                                Text(item, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!withQuantity) TextButton(onClick = { withQuantity = true }) { Text(stringResource(R.string.quantity)) }
                Spacer(Modifier.weight(1f))
                if (added.isNotEmpty()) TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                Button(onClick = add, enabled = name.isNotBlank()) { Text(stringResource(R.string.add)) }
            }
        }
    }
}
