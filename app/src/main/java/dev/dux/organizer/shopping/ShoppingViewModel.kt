package dev.dux.organizer.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.ShoppingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [current] is null only while there are no lists (right after restoring an old backup). */
data class ShoppingState(
    val lists: List<ShoppingList>,
    val current: ShoppingList?,
    val pending: List<ShoppingItem>,
    val inCart: List<ShoppingItem>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(private val repo: ShoppingRepository) : ViewModel() {
    private val selectedId = MutableStateFlow<String?>(null)

    /** null = still loading. A deleted or missing selection falls back to the first list. */
    val state = combine(repo.lists, selectedId) { lists, id -> lists to (lists.firstOrNull { it.id == id } ?: lists.firstOrNull()) }
        .flatMapLatest { (lists, current) ->
            if (current == null) {
                flowOf(ShoppingState(lists, null, emptyList(), emptyList()))
            } else {
                repo.items(current.id).map { items ->
                    val (inCart, pending) = items.partition { it.checked }
                    ShoppingState(lists, current, pending, inCart)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val currentId get() = state.value?.current?.id

    fun select(id: String) {
        selectedId.value = id
    }

    fun createList(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = repo.createList(name)
            if (id != null) selectedId.value = id
            onResult(id != null)
        }
    }

    fun renameList(name: String, onResult: (Boolean) -> Unit) {
        val id = currentId ?: return
        viewModelScope.launch { onResult(repo.renameList(id, name)) }
    }

    fun deleteList() {
        val id = currentId ?: return
        viewModelScope.launch { repo.deleteList(id) }
    }

    fun add(name: String, quantity: String, onResult: (Boolean) -> Unit) {
        val id = currentId ?: return
        viewModelScope.launch { onResult(repo.addItem(id, name, quantity)) }
    }

    fun toggle(item: ShoppingItem) = launch { repo.setChecked(item.id, !item.checked) }
    fun delete(item: ShoppingItem) = launch { repo.deleteItem(item.id) }
    fun removeChecked() = currentId?.let { id -> launch { repo.removeChecked(id) } }
    fun uncheckAll() = currentId?.let { id -> launch { repo.uncheckAll(id) } }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
