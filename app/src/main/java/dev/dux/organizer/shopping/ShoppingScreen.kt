package dev.dux.organizer.shopping

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.dux.organizer.ui.inBlock
import dev.dux.organizer.R
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.ui.NameDialog
import dev.dux.organizer.ui.ScreenHeader
import dev.dux.organizer.ui.SwipeToDelete
import dev.dux.organizer.ui.asHeading

private enum class ListDialog { Create, Rename, Delete }

@OptIn(ExperimentalLayoutApi::class)
@Composable
/** [offerUndo] shows a message with "Deshacer" after something is deleted. */
fun ShoppingScreen(vm: ShoppingViewModel, offerUndo: (String, () -> Unit) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<ListDialog?>(null) }
    val context = LocalContext.current
    val current = state ?: return
    val list = current.current

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            ScreenHeader(
                R.string.tab_shopping,
                onSearch,
                extra = {
                    if (list != null) {
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shoppingText(list.name, current.pending))
                                context.startActivity(Intent.createChooser(send, null))
                            },
                            enabled = current.pending.isNotEmpty(),
                        ) { Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_list)) }
                    }
                },
                menu = { close ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.new_list)) }, onClick = { close(); dialog = ListDialog.Create })
                    if (list != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename_list)) }, onClick = { close(); dialog = ListDialog.Rename })
                        // The last list cannot be deleted: the tab always has one.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_list)) },
                            onClick = { close(); dialog = ListDialog.Delete },
                            enabled = current.lists.size > 1,
                        )
                    }
                },
            )
        }
        item {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.lists.forEach { option ->
                    val selected = option.id == list?.id
                    FilterChip(
                        selected = selected,
                        onClick = { vm.select(option.id) },
                        // "Súper · 5": what is still to buy in each list.
                        label = { Text(current.pendingByList[option.id]?.let { "${option.name} · $it" } ?: option.name) },
                        leadingIcon = if (selected) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    )
                }
            }
        }
        if (list != null) item { QuickAdd(onAdd = vm::add) }
        if (list != null && current.pending.isEmpty() && current.inCart.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_shopping_list),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        if (current.pending.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.to_buy, current.pending.size)) }
            itemsIndexed(current.pending, key = { _, item -> item.id }) { index, item ->
                // Swipe to remove, like the Bandeja: a trash icon on every row was easy to hit by mistake in the store.
                SwipeToDelete(onDelete = {
                    vm.delete(item) { deleted -> offerUndo(resources.getString(R.string.item_removed, deleted.name)) { vm.restore(listOf(deleted)) } }
                }, label = R.string.remove) {
                    ItemRow(item, onToggle = { vm.toggle(item) }, Modifier.inBlock(index, current.pending.lastIndex))
                }
            }
        }
        if (current.inCart.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.in_cart, current.inCart.size)) {
                    TextButton(onClick = { vm.uncheckAll() }) { Text(stringResource(R.string.uncheck_all)) }
                    TextButton(onClick = {
                        vm.removeChecked { removed ->
                            if (removed.isNotEmpty()) offerUndo(resources.getQuantityString(R.plurals.items_removed, removed.size, removed.size)) { vm.restore(removed) }
                        }
                    }) { Text(stringResource(R.string.remove_checked)) }
                }
            }
            itemsIndexed(current.inCart, key = { _, item -> item.id }) { index, item ->
                ItemRow(item, onToggle = { vm.toggle(item) }, Modifier.inBlock(index, current.inCart.lastIndex), MaterialTheme.colorScheme.surfaceContainerLow)
            }
        }
    }

    when (dialog) {
        ListDialog.Create -> NameDialog(
            title = R.string.new_list,
            label = R.string.list_name,
            onDismiss = { dialog = null },
            onConfirm = { name, done -> vm.createList(name) { ok -> if (ok) dialog = null; done(ok) } },
        )
        ListDialog.Rename -> if (list != null) NameDialog(
            title = R.string.rename_list,
            label = R.string.list_name,
            initial = list.name,
            confirm = R.string.save,
            onDismiss = { dialog = null },
            onConfirm = { name, done -> vm.renameList(name) { ok -> if (ok) dialog = null; done(ok) } },
        )
        ListDialog.Delete -> if (list != null) {
            val count = current.pending.size + current.inCart.size
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.delete_list_confirm_title, list.name)) },
                text = { Text(if (count == 0) stringResource(R.string.delete_confirm_body) else pluralStringResource(R.plurals.delete_list_confirm_body, count, count)) },
                confirmButton = { TextButton(onClick = { vm.deleteList(); dialog = null }) { Text(stringResource(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        null -> {}
    }
}

/**
 * A section title with optional actions at its end ("Desmarcar todo", "Quitar comprados"). The actions
 * wrap below at large font sizes; in a Row they crushed the title into one letter per line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionTitle(text: String, actions: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).asHeading())
        Row { actions() }
    }
}

/**
 * Adds items right in the list, without opening a sheet: type, press Enter, and the field stays ready for
 * the next one. The quantity is optional and only shows when asked for.
 */
@Composable
private fun QuickAdd(onAdd: (String, String, (Boolean) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var withQuantity by rememberSaveable { mutableStateOf(false) }
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
                withQuantity = false
                focus.requestFocus()
            }
        }
    }
    val plain = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                TextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    placeholder = { Text(stringResource(R.string.add_item_hint)) },
                    singleLine = true,
                    isError = error,
                    colors = plain,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (!withQuantity) TextButton(onClick = { withQuantity = true }) { Text(stringResource(R.string.quantity)) }
            }
            if (withQuantity) {
                TextField(
                    value = quantity,
                    onValueChange = { quantity = it; error = false },
                    placeholder = { Text(stringResource(R.string.quantity_optional)) },
                    singleLine = true,
                    colors = plain,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                )
            }
            val message = if (error) stringResource(R.string.item_invalid) else lastAdded?.let { stringResource(R.string.added_item, it) }
            message?.let {
                // Confirms each item (TalkBack announces it): the field clears right away.
                Text(
                    it,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The whole row toggles the check mark: easy to hit with one hand in the store. The quantity is a tag at the end. */
@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceContainer) {
    ListItem(
        headlineContent = {
            Text(
                item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        },
        modifier = modifier.toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = { Checkbox(checked = item.checked, onCheckedChange = null) },
        trailingContent = item.quantity?.let {
            {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        },
    )
}
