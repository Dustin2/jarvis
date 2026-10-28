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
    projectName: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val done = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
    ListItem(
        onClick = onOpen,
        // null when there is nothing to show: the row stays single-line.
        supportingContent = if (
            task.priority != Priority.NONE || task.dueDate != null || task.scheduledDate != null ||
            projectName != null || task.notes != null || task.recurrence != null
        ) ({ Supporting(task, projectName) }) else null,
        leadingContent = onToggleDone?.let { toggle ->
            {
                // With the task name: TalkBack reads the checkbox separately and "Completar" alone does not say which.
                val description = stringResource(R.string.complete_task, task.title)
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
