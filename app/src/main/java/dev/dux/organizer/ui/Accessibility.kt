package dev.dux.organizer.ui

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import dev.dux.organizer.R
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Screen and section titles: TalkBack can jump between them. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

/**
 * A short label in a row of equal slots (bottom bar, tabs). At 200 % font size they do not fit the phone
 * width and break or get cut ("Movimie"): these grow up to 130 %; the rest of the app follows the system size.
 */
@Composable
fun CappedLabel(text: String) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
        Text(text, maxLines = 1)
    }
}

/**
 * The "+" floating button with its text. Material hides an extended FAB's text from TalkBack
 * (clearAndSetSemantics), so without the description it is announced as an unnamed button.
 */
@Composable
fun AddButton(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(text) },
        modifier = Modifier.semantics { contentDescription = text },
    )
}
