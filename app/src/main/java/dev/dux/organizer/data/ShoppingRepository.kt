package dev.dux.organizer.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock

class ShoppingRepository(private val dao: ShoppingDao, private val clock: Clock = Clock.systemUTC()) {

    val lists: Flow<List<ShoppingList>> = dao.observeLists()

    fun items(listId: String): Flow<List<ShoppingItem>> = dao.observeItems(listId)

    // "Empty table" = first run: holds because the last list cannot be deleted. An old backup without
    // lists also leaves it empty, and the next start creates "Súper" again.
    suspend fun seedIfEmpty() {
        val now = clock.instant()
        dao.seedIfEmpty(ShoppingList(name = INITIAL_LIST, createdAt = now, updatedAt = now))
    }

    /** Returns the new list's id, or null if the name is not valid. */
    suspend fun createList(name: String): String? {
        val clean = cleanTitle(name) ?: return null
        val now = clock.instant()
        return ShoppingList(name = clean, sortOrder = dao.nextListSortOrder(), createdAt = now, updatedAt = now)
            .also { dao.insertList(it) }.id
    }

    suspend fun renameList(id: String, name: String): Boolean {
        val clean = cleanTitle(name) ?: return false
        val current = dao.getList(id) ?: return true
        if (current.name != clean) dao.updateList(current.copy(name = clean, updatedAt = clock.instant()))
        return true
    }

    suspend fun deleteList(id: String) = dao.deleteListIfNotLast(id)

    /** Returns false if the name or the quantity is not valid. A blank quantity means none. */
    suspend fun addItem(listId: String, name: String, quantity: String = ""): Boolean {
        val clean = cleanTitle(name) ?: return false
        val amount = quantity.trim().ifEmpty { null }
        if (amount != null && amount.length > MAX_QUANTITY_LENGTH) return false
        val now = clock.instant()
        dao.insertItem(
            ShoppingItem(listId = listId, name = clean, quantity = amount, sortOrder = dao.nextItemSortOrder(listId), createdAt = now, updatedAt = now)
        )
        return true
    }

    suspend fun setChecked(itemId: String, checked: Boolean) {
        val current = dao.getItem(itemId) ?: return
        if (current.checked != checked) dao.updateItem(current.copy(checked = checked, updatedAt = clock.instant()))
    }

    suspend fun deleteItem(itemId: String) = dao.deleteItem(itemId)

    /** "Quitar comprados". */
    suspend fun removeChecked(listId: String) = dao.deleteChecked(listId)

    /** "Desmarcar todo": for lists bought every week. */
    suspend fun uncheckAll(listId: String) = dao.uncheckAll(listId, clock.instant())

    companion object {
        const val INITIAL_LIST = "Súper"
        const val MAX_QUANTITY_LENGTH = 40
    }
}
