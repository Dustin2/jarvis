package dev.dux.organizer.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Títulos de pantalla y de sección: TalkBack permite saltar entre ellos. */
fun Modifier.asHeading(): Modifier = semantics { heading() }
