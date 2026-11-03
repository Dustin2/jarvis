package dev.dux.organizer.reminders

import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import java.time.LocalDateTime

sealed interface ReminderAction {
    /** There should be no alarm: no reminder, or the task is finished or deleted. */
    data object Cancel : ReminderAction
    data class Schedule(val at: LocalDateTime) : ReminderAction
    /** It is time (or it passed while the phone was off): notify now. */
    data object NotifyNow : ReminderAction
}

/** Margin so an alarm Android fired a few seconds early is not rescheduled. */
private const val TOLERANCE_SECONDS = 30L

/**
 * The single reminder rule. Applied on edit, when the alarm fires and when rescheduling everything
 * (reboot, time change): since it always decides from the task's current state,
 * a stale alarm never notifies about something that no longer applies.
 */
fun reminderAction(task: Task?, now: LocalDateTime): ReminderAction {
    val at = task?.reminderAt
    return when {
        at == null || task.deletedAt != null || task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED -> ReminderAction.Cancel
        at <= now.plusSeconds(TOLERANCE_SECONDS) -> ReminderAction.NotifyNow
        else -> ReminderAction.Schedule(at)
    }
}
