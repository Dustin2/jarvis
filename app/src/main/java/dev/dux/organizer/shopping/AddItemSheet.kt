package dev.dux.organizer.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import dev.dux.organizer.ui.Drafts

/**
 * Adds items in a burst: after each one the fields clear and the keyboard stays open for the next.
 * [onAdd] receives the name, the quantity and a callback saying whether it was saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(listName: String, drafts: Drafts, onDismiss: () -> Unit, onAdd: (String, String, (Boolean) -> Unit) -> Unit) {
    // Kept by the screen: closing the sheet by accident does not lose what was typed.
    var name by drafts.field("item.name")
    var quantity by drafts.field("item.quantity")
    var error by rememberSaveable { mutableStateOf(false) }
    var lastAdded by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val add = {
        val added = name.trim()
        onAdd(name, quantity) { ok ->
            error = !ok
            if (ok) {
                lastAdded = added
                name = ""
                quantity = ""
                focus.requestFocus()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            // Scrolls: in landscape or with large fonts the keyboard covers the quantity field.
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_to_list, listName), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = false },
                label = { Text(stringResource(R.string.item_name)) },
                isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.item_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it; error = false },
                label = { Text(stringResource(R.string.quantity_optional)) },
                singleLine = true,
                // "2 kg", not "2 Kg".
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
            // Feedback for each item, since the list behind the sheet is hidden. TalkBack announces it.
            lastAdded?.let {
                Text(
                    stringResource(R.string.added_item, it),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(onClick = add, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.add_item)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
