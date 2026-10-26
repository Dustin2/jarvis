package dev.dux.organizer.today

import dev.dux.organizer.data.Task
import java.time.LocalDate

data class DayPlan(
    val overdue: List<Task>,
    val today: List<Task>,
    val upcoming: List<Task>,
    val openCount: Int,
) {
    /** Lo vencido va primero: ya debía estar hecho. Dentro de cada grupo se respeta el orden de entrada. */
    val nextStep: Task? get() = overdue.firstOrNull() ?: today.firstOrNull()
}

const val UPCOMING_DAYS = 7L

/**
 * Reglas de "Mi día" sobre tareas abiertas (ya ordenadas por prioridad):
 * - Vencida: su fecha límite ya pasó.
 * - Hoy: vence hoy, o se programó para hoy o un día anterior (lo planeado y no hecho se arrastra; no "vence").
 * - Próximas: vence o está programada en los siguientes [UPCOMING_DAYS] días.
 * Cada tarea aparece en un solo grupo, el primero que aplique.
 */
fun planDay(open: List<Task>, today: LocalDate): DayPlan {
    val horizon = today.plusDays(UPCOMING_DAYS)
    val overdue = mutableListOf<Task>()
    val forToday = mutableListOf<Task>()
    val upcoming = mutableListOf<Task>()
    for (task in open) {
        val due = task.dueDate
        val scheduled = task.scheduledDate
        when {
            due != null && due < today -> overdue += task
            due == today || (scheduled != null && scheduled <= today) -> forToday += task
            listOfNotNull(due, scheduled).any { it <= horizon } -> upcoming += task
        }
    }
    upcoming.sortBy { listOfNotNull(it.dueDate, it.scheduledDate).min() }
    return DayPlan(overdue, forToday, upcoming, open.size)
}
