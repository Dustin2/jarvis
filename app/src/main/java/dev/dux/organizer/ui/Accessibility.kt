package dev.dux.organizer.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Screen and section titles: TalkBack can jump between them. */
fun Modifier.asHeading(): Modifier = semantics { heading() }
