package dev.dux.organizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dux.organizer.R

/**
 * The tabs on a floating pill, with the capture button as a round button beside it. Labels fold away while
 * scrolling down (more room for the list) and come back on scrolling up; [compact] says which.
 */
@Composable
fun FloatingNav(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    compact: Boolean,
    onCapture: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f).animateContentSize(),
        ) {
            Row(Modifier.padding(6.dp).selectableGroup()) {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    val pill by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent)
                    val label = stringResource(item.label)
                    Column(
                        Modifier
                            .weight(1f)
                            .background(pill, CircleShape)
                            .selectable(selected, role = Role.Tab) { onTabChange(item) }
                            .semantics { contentDescription = label }
                            .padding(vertical = if (compact) 10.dp else 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
                            Icon(painterResource(item.icon), contentDescription = null)
                            AnimatedVisibility(!compact, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                ProvideTextStyle(MaterialTheme.typography.labelSmall) { CappedLabel(label) }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(onCapture != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            val text = stringResource(R.string.capture)
            FloatingActionButton(
                onClick = { onCapture?.invoke() },
                shape = CircleShape,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                modifier = Modifier.size(60.dp).semantics { contentDescription = text },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_add), contentDescription = null) }
            }
        }
    }
}

/** Folds the [FloatingNav] labels while the content scrolls down and unfolds them on the way up. */
class NavFold(private val onCompact: (Boolean) -> Unit) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -6f) onCompact(true) else if (available.y > 6f) onCompact(false)
        return Offset.Zero
    }
}
