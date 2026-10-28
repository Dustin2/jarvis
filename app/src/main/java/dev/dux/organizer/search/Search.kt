package dev.dux.organizer.search

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.Debt
import dev.dux.organizer.data.Movement
import dev.dux.organizer.data.Project
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import java.text.Normalizer
import java.util.Locale

private val accents = Regex("\\p{Mn}+")

/** Lower case without accents: "Café" and "cafe" compare equal. */
fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(accents, "").lowercase(Locale.ROOT)

/**
 * Where [query] appears in [text], ignoring case and accents, as indices of the original [text] (to bold it).
 * Each character is normalized on its own so the indices map back one to one.
 */
fun matchRange(text: String, query: String): IntRange? {
    val q = normalize(query.trim())
    if (q.isEmpty()) return null
    val normalized = StringBuilder()
    val origin = ArrayList<Int>() // normalized index → original index
    text.forEachIndexed { i, c ->
        normalize(c.toString()).forEach { normalized.append(it); origin.add(i) }
    }
    val start = normalized.indexOf(q).takeIf { it >= 0 } ?: return null
    return origin[start]..origin[start + q.length - 1]
}

private fun String?.matches(query: String) = this != null && matchRange(this, query) != null

data class SearchData(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val lists: List<ShoppingList> = emptyList(),
    val items: List<ShoppingItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val movements: List<Movement> = emptyList(),
    val debts: List<Debt> = emptyList(),
)

data class ItemHit(val item: ShoppingItem, val listName: String)
data class MovementHit(val movement: Movement, val categoryName: String)

data class SearchResults(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val items: List<ItemHit> = emptyList(),
    val movements: List<MovementHit> = emptyList(),
    val debts: List<Debt> = emptyList(),
) {
    val isEmpty get() = tasks.isEmpty() && projects.isEmpty() && items.isEmpty() && movements.isEmpty() && debts.isEmpty()
}

private val openStatuses = setOf(TaskStatus.INBOX, TaskStatus.TODO)

// shortcut: filters everything in memory, which also allows ignoring accents (SQLite LIKE does not).
// Instant for personal data; with tens of thousands of rows, move to an SQLite FTS table.
fun search(query: String, data: SearchData): SearchResults {
    if (query.isBlank()) return SearchResults()
    val listNames = data.lists.associate { it.id to it.name }
    val categoryNames = data.categories.associate { it.id to it.name }
    return SearchResults(
        tasks = data.tasks.filter { it.title.matches(query) || it.notes.matches(query) }
            .sortedWith(compareBy<Task> { it.status !in openStatuses }.thenByDescending { it.updatedAt }),
        projects = data.projects.filter { it.name.matches(query) },
        items = data.items.filter { it.name.matches(query) || it.quantity.matches(query) }
            .sortedBy { it.checked }
            .map { ItemHit(it, listNames[it.listId].orEmpty()) },
        movements = data.movements.filter { it.note.matches(query) || categoryNames[it.categoryId].matches(query) }
            .sortedWith(compareByDescending<Movement> { it.date }.thenByDescending { it.createdAt })
            .map { MovementHit(it, categoryNames[it.categoryId].orEmpty()) },
        debts = data.debts.filter { it.person.matches(query) || it.note.matches(query) }.sortedBy { it.settledAt != null },
    )
}
