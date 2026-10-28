package dev.dux.organizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Text typed in a sheet, kept by the screen that opens it: closing the sheet by accident ("back" with the
 * keyboard open, a swipe) no longer loses it, and reopening shows it again. Cleared once saved.
 * Survives rotation and process death (saved state).
 */
class Drafts(private val texts: SnapshotStateMap<String, String>) {
    /** A field to use as `var title by drafts.field("capture.title")`. */
    fun field(key: String): MutableState<String> = object : MutableState<String> {
        override var value: String
            get() = texts[key].orEmpty()
            set(v) { if (v.isEmpty()) texts.remove(key) else texts[key] = v }
        override fun component1() = value
        override fun component2(): (String) -> Unit = { value = it }
    }

    fun snapshot(): List<String> = texts.flatMap { (k, v) -> listOf(k, v) }

    /** Forgets every field whose key starts with [prefix] ("capture."). */
    fun clear(prefix: String) {
        texts.keys.filter { it.startsWith(prefix) }.forEach(texts::remove)
    }
}

@Composable
fun rememberDrafts(): Drafts = rememberSaveable(
    saver = listSaver(save = { it.snapshot() }, restore = { Drafts(mutableStateMapOf<String, String>().apply { putAll(it.chunked(2).associate { (k, v) -> k to v }) }) }),
) { Drafts(mutableStateMapOf()) }
