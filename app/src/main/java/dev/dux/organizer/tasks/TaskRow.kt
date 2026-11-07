package dev.dux.organizer.tasks

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

import androidx.annotation.StringRes
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.dux.organizer.R
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.ui.inlineLabel
import dev.dux.organizer.ui.rememberToday

@get:StringRes
val TaskStatus.label: Int
    get() = when (this) {
        TaskStatus.INBOX -> R.string.status_inbox
        TaskStatus.TODO -> R.string.status_todo
        TaskStatus.DONE -> R.string.status_done
        TaskStatus.CANCELLED -> R.string.status_cancelled
    }

@get:StringRes
val Priority.label: Int
    get() = when (this) {
        Priority.NONE -> R.string.priority_none
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
    }

/** Task row: the checkbox completes/reopens, tapping the row opens the detail. */
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onToggleDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    // Completing: the check pops with a burst of dots, the row tints and strikes through, then slides away,
    // with a tap of vibration. Without it the row vanished the instant it was tapped, with no sign it worked.
    var completing by remember(task.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val pop = remember(task.id) { Animatable(1f) }
    val burst = remember(task.id) { Animatable(0f) }
    val leave = remember(task.id) { Animatable(0f) }
    LaunchedEffect(completing) {
        if (completing) {
            launch { pop.animateTo(1f, keyframes { durationMillis = POP_MS; 0.6f at 0; 1.28f at POP_MS * 55 / 100 }) }
            launch { burst.animateTo(1f, tween(POP_MS + 100, easing = LinearOutSlowInEasing)) }
            delay(HOLD_MS)
            leave.animateTo(1f, tween(LEAVE_MS, easing = FastOutLinearInEasing))
            onToggleDone?.invoke()
            completing = false
            // Back in place in case the row stays (an undo, or a list that keeps finished tasks).
            leave.snapTo(0f)
            burst.snapTo(0f)
        }
    }
    val done = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED || completing
    val tint by animateColorAsState(
        if (completing) MaterialTheme.colorScheme.primaryContainer else containerColor.takeOrElse { ListItemDefaults.colors().containerColor },
        label = "completing",
    )
    val slide = with(LocalDensity.current) { 48.dp.toPx() }
    val rowModifier = modifier.graphicsLayer {
        translationX = leave.value * slide
        alpha = 1f - leave.value
    }
    ListItem(
        onClick = onOpen,
        modifier = rowModifier,
        shapes = shapes,
        colors = ListItemDefaults.colors(containerColor = tint),
        // null when there is nothing to show: the row stays single-line.
        supportingContent = if (
            task.priority != Priority.NONE || task.dueDate != null || task.scheduledDate != null ||
            projectName != null || task.notes != null || task.recurrence != null
        ) ({ Supporting(task, projectName) }) else null,
        leadingContent = onToggleDone?.let { toggle ->
            {
                // With the task name: TalkBack reads the checkbox separately and "Completar" alone does not say which.
                val description = stringResource(R.string.complete_task, task.title)
                Box(contentAlignment = Alignment.Center) {
                    Burst(burst.value)
                    Checkbox(
                    checked = task.status == TaskStatus.DONE || completing,
                    onCheckedChange = {
                        when {
                            completing -> Unit
                            task.status in OPEN -> {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                completing = true
                            }
                            else -> toggle()
                        }
                    },
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.semantics { contentDescription = description },
                )
                }
            }
        },
        trailingContent = trailing,
    ) {
        Text(
            task.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun Supporting(task: Task, projectName: String?) {
    val today by rememberToday()
    val overdue = task.dueDate?.let { it < today } == true && task.status in OPEN
    val parts = buildList {
        if (task.priority != Priority.NONE) add(stringResource(task.priority.label))
        task.dueDate?.let { add(stringResource(R.string.due_label, it.inlineLabel(today))) }
            // Planned for a past day and not done yet: it carries over ("Desde ayer"), it is not overdue.
            ?: task.scheduledDate?.let { add(stringResource(if (it < today) R.string.scheduled_since else R.string.scheduled_label, it.inlineLabel(today))) }
        if (task.recurrence != null) add(stringResource(R.string.repeats))
        projectName?.let { add(it) }
        task.notes?.let { add(it) }
    }
    Text(
        parts.joinToString(" · "),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = when {
            overdue -> MaterialTheme.colorScheme.error
            task.priority == Priority.HIGH -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private val OPEN = setOf(TaskStatus.INBOX, TaskStatus.TODO)

/** Eight dots flying out of the checkbox; [progress] 0..1. Drawn behind it, outside its bounds. */
@Composable
private fun Burst(progress: Float) {
    if (progress == 0f || progress == 1f) return
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    Canvas(Modifier.size(48.dp)) {
        val distance = size.minDimension * (0.25f + 0.45f * progress)
        val radius = 3.dp.toPx() * (1f - 0.6f * progress)
        repeat(8) { i ->
            val angle = Math.toRadians(i * 45.0 - 90)
            drawCircle(
                colors[i % colors.size].copy(alpha = 1f - progress),
                radius,
                center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat()),
            )
        }
    }
}

// Long enough to see the check pop, short enough not to feel slow: about 0.75 s until the row is gone.
private const val POP_MS = 450
private const val HOLD_MS = 500L
private const val LEAVE_MS = 250
