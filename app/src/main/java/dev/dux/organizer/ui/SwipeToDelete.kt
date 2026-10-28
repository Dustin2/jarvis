package dev.dux.organizer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R

/**
 * Swipe towards the start to delete. Swiping is invisible to TalkBack, so the row also gets an
 * accessibility action with the same label ("Eliminar", "Quitar").
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, @StringRes label: Int = R.string.delete, content: @Composable () -> Unit) {
    // remember, not rememberSaveable: a row brought back by "Deshacer" must not come back already dismissed.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val label = stringResource(label)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true }) },
        backgroundContent = {
            // Only while swiping: at rest the red would show through the row's rounded corners.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(label, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}
