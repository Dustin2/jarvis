package dev.dux.organizer.tasks

import androidx.annotation.StringRes
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
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
import dev.dux.organizer.ui.label
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

/** Fila de tarea: la casilla completa/reabre, tocar la fila abre el detalle. */
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onToggleDone: (() -> Unit)?,
    projectName: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val done = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
    ListItem(
        onClick = onOpen,
        // null cuando no hay nada que mostrar: así la fila queda de una sola línea.
        supportingContent = if (
            task.priority != Priority.NONE || task.dueDate != null || task.scheduledDate != null ||
            projectName != null || task.notes != null
        ) ({ Supporting(task, projectName) }) else null,
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
        task.dueDate?.let { add(stringResource(R.string.due_label, it.label(today))) }
            ?: task.scheduledDate?.let { add(stringResource(R.string.scheduled_label, it.label(today))) }
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
