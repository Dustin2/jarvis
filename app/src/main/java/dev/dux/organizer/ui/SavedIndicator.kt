package dev.dux.organizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R
import kotlinx.coroutines.delay

const val SAVED_VISIBLE_MILLIS = 1_500L

/**
 * Shows "Guardado" each time [savedVersion] changes after the first value: pass the record's `updatedAt`,
 * so it only appears once the database actually stored the change. It stays while the user keeps typing
 * and fades [SAVED_VISIBLE_MILLIS] after the last change. TalkBack announces it (live region).
 */
@Composable
fun SavedIndicator(savedVersion: Any?) {
    var visible by remember { mutableStateOf(false) }
    // The first value is the loaded record, not a save. remember (not saveable): rotation does not re-trigger it.
    var last by remember { mutableStateOf(savedVersion) }
    LaunchedEffect(savedVersion) {
        val previous = last
        last = savedVersion
        if (previous == null || savedVersion == null || previous == savedVersion) return@LaunchedEffect
        visible = true
        delay(SAVED_VISIBLE_MILLIS)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.saved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
