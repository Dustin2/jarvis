package dev.dux.organizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

// Own tables, not tasks: an item only needs a name, a quantity and a check mark (docs/design/shopping-lists.md).
@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(entity = ShoppingList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("listId")],
)
data class ShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val name: String,
    /** Free text ("2", "1 kg", "un paquete"). */
    val quantity: String? = null,
    /** In the cart. */
    val checked: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Every item ever added, by name, for the suggestions while adding: it outlives the items ("Quitar comprados"). */
@Entity(tableName = "shopping_history")
data class ShoppingHistory(
    /** [itemKey] of the name: "Leche" and "leché " are the same thing. */
    @PrimaryKey val nameKey: String,
    /** As it was written the last time. */
    val name: String,
    val timesAdded: Int,
    val lastAddedAt: Instant,
)

/** Lowercase, trimmed, without accents. */
fun itemKey(name: String): String =
    java.text.Normalizer.normalize(name.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
