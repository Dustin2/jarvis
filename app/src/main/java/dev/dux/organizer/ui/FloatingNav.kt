package dev.dux.organizer.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
 * The tabs on a floating pill. The selected tab's indicator glides from tab to tab on a spring, and the labels
 * fold away while scrolling down (more room for the list) and come back on scrolling up; [compact] says which.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNav(tab: Tab, onTabChange: (Tab) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    ) {
        BoxWithConstraints(Modifier.padding(6.dp)) {
            val slot = maxWidth / Tab.entries.size
            val x by animateDpAsState(slot * tab.ordinal, motion.defaultSpatialSpec(), label = "indicator")
            Box(
                Modifier.matchParentSize().wrapContentWidth(Alignment.Start).offset { IntOffset(x.roundToPx(), 0) }.width(slot)
                    .background(colors.secondaryContainer, CircleShape),
            )
            Row(Modifier.selectableGroup()) {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    val tint by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, motion.defaultEffectsSpec(), label = "tint")
                    val label = stringResource(item.label)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .selectable(selected, role = Role.Tab) { onTabChange(item) }
                            .semantics { contentDescription = label }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides tint) {
                            Icon(painterResource(item.icon), contentDescription = null)
                            AnimatedVisibility(
                                !compact,
                                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.labelSmall) { CappedLabel(label) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The round "+" floating above the bar, the same on every tab; [text] says what it adds, for TalkBack.
 * It pops in and out on a spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundAddButton(text: String, visible: Boolean = true, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(64.dp).semantics { contentDescription = text },
        ) { Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(28.dp)) }
    }
}

/**
 * Folds the [FloatingNav] labels after scrolling down a little and unfolds them after scrolling up a little.
 * Counting the distance (not each event) keeps the bar from flickering on small finger jitters.
 */
class NavFold(private val onCompact: (Boolean) -> Unit) : NestedScrollConnection {
    private var run = 0f
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y == 0f) return Offset.Zero
        if ((available.y < 0) != (run < 0)) run = 0f
        run += available.y
        if (run < -48f) onCompact(true) else if (run > 48f) onCompact(false)
        return Offset.Zero
    }
}
