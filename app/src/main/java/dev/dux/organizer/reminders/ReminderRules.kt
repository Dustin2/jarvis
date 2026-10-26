package dev.dux.organizer.reminders

import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskStatus
import java.time.LocalDateTime

sealed interface ReminderAction {
    /** No debe haber alarma: sin recordatorio, tarea terminada o borrada. */
    data object Cancel : ReminderAction
    data class Schedule(val at: LocalDateTime) : ReminderAction
    /** Ya es la hora (o se pasó mientras el teléfono estaba apagado): avisar ahora. */
    data object NotifyNow : ReminderAction
}

/** Margen para no reprogramar una alarma que Android disparó unos segundos antes de tiempo. */
private const val TOLERANCE_SECONDS = 30L

/**
 * Única regla de recordatorios. Se aplica al editar, al sonar la alarma y al reprogramar todo
 * (reinicio, cambio de hora): como siempre se decide desde el estado actual de la tarea,
 * una alarma vieja nunca avisa de algo que ya no aplica.
 */
fun reminderAction(task: Task?, now: LocalDateTime): ReminderAction {
    val at = task?.reminderAt
    return when {
        at == null || task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED -> ReminderAction.Cancel
        at <= now.plusSeconds(TOLERANCE_SECONDS) -> ReminderAction.NotifyNow
        else -> ReminderAction.Schedule(at)
    }
}
