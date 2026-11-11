package dev.dux.organizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY sortOrder, name")
    fun observeLists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY sortOrder")
    fun observeItems(listId: String): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    fun observeAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: String): ShoppingList?

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItem(id: String): ShoppingItem?

    @Query("SELECT * FROM shopping_lists ORDER BY sortOrder")
    suspend fun getAllLists(): List<ShoppingList>

    @Query("SELECT * FROM shopping_items ORDER BY listId, sortOrder")
    suspend fun getAllItems(): List<ShoppingItem>

    @Query("SELECT COUNT(*) FROM shopping_lists")
    suspend fun countLists(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM shopping_lists")
    suspend fun nextListSortOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM shopping_items WHERE listId = :listId")
    suspend fun nextItemSortOrder(listId: String): Int

    @Insert suspend fun insertList(list: ShoppingList)
    @Insert suspend fun insertLists(lists: List<ShoppingList>)
    @Insert suspend fun insertItem(item: ShoppingItem)

    @Query("SELECT * FROM shopping_history") fun observeHistory(): Flow<List<ShoppingHistory>>
    @Query("SELECT * FROM shopping_history WHERE nameKey = :key") suspend fun getHistory(key: String): ShoppingHistory?
    @Upsert suspend fun upsertHistory(entry: ShoppingHistory)

    /** Adds it and remembers it for the suggestions. */
    @Transaction
    suspend fun insertItemRemembered(item: ShoppingItem) {
        insertItem(item)
        val key = itemKey(item.name)
        val seen = getHistory(key)
        upsertHistory(seen?.copy(name = item.name, timesAdded = seen.timesAdded + 1, lastAddedAt = item.createdAt) ?: ShoppingHistory(key, item.name, 1, item.createdAt))
    }
    @Insert suspend fun insertItems(items: List<ShoppingItem>)
    @Update suspend fun updateList(list: ShoppingList)
    @Update suspend fun updateItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM shopping_items WHERE listId = :listId AND checked = 1")
    suspend fun deleteChecked(listId: String)

    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND checked = 1")
    suspend fun getChecked(listId: String): List<ShoppingItem>

    /** Returns what it deleted, so it can be undone. */
    @Transaction
    suspend fun removeChecked(listId: String): List<ShoppingItem> = getChecked(listId).also { deleteChecked(listId) }

    @Query("UPDATE shopping_items SET checked = 0, updatedAt = :now WHERE listId = :listId AND checked = 1")
    suspend fun uncheckAll(listId: String, now: Instant)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    /** The items go with it (ON DELETE CASCADE). The last list stays: the tab always has one. */
    @Transaction
    suspend fun deleteListIfNotLast(id: String) {
        if (countLists() > 1) deleteList(id)
    }

    @Transaction
    suspend fun seedIfEmpty(list: ShoppingList) {
        if (countLists() == 0) insertList(list)
    }

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAllLists()
}
