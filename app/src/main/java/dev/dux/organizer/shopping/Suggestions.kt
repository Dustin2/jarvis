package dev.dux.organizer.shopping

import dev.dux.organizer.data.ShoppingHistory
import dev.dux.organizer.data.itemKey

/**
 * What to offer while adding to a list: what is added most often first, then names from the other lists (so there
 * are suggestions from day one). Never what is already on [onList]. With nothing [typed], the usual things;
 * otherwise names starting with it, then names with a word starting with it ("leche" for "deslactosada" too).
 */
fun suggestItems(typed: String, history: List<ShoppingHistory>, known: List<String>, onList: List<String>, limit: Int = 6): List<String> {
    val taken = onList.mapTo(HashSet(), ::itemKey)
    val q = itemKey(typed)
    val usual = history.sortedWith(compareByDescending<ShoppingHistory> { it.timesAdded }.thenByDescending { it.lastAddedAt }).map { it.name }
    val all = (usual + known).distinctBy(::itemKey).filter { itemKey(it) !in taken }
    if (q.isEmpty()) return all.take(limit)
    val (starts, rest) = all.partition { itemKey(it).startsWith(q) }
    return (starts + rest.filter { name -> itemKey(name).split(' ').any { it.startsWith(q) } }).take(limit)
}
