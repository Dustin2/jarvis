package dev.dux.organizer.shopping

import dev.dux.organizer.data.ShoppingItem

/** Plain text to send by WhatsApp or anything else: what is still missing, one item per line. */
fun shoppingText(listName: String, pending: List<ShoppingItem>): String = buildString {
    append(listName).append(':')
    pending.forEach { item ->
        append("\n- ").append(item.name)
        item.quantity?.let { append(" (").append(it).append(')') }
    }
}
