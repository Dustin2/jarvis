package dev.dux.organizer.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Fila de tarea: la casilla completa/reabre, tocar la fila abre el detalle. */
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onToggleDone: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val done = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
            Text(
                task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = supporting(task),
        leadingContent = onToggleDone?.let { toggle ->
            {
                val description = stringResource(R.string.mark_done)
                Checkbox(
                    checked = task.status == TaskStatus.DONE,
                    onCheckedChange = { toggle() },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        },
        trailingContent = trailing,
    )
}

private fun supporting(task: Task): (@Composable () -> Unit)? {
    val showPriority = task.priority != Priority.NONE
    if (!showPriority && task.notes == null) return null
    return {
        val text = buildList {
            if (showPriority) add(stringResource(R.string.priority) + ": " + stringResource(task.priority.label))
            task.notes?.let { add(it) }
        }.joinToString(" · ")
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (task.priority == Priority.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
