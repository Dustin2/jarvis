package dev.dux.organizer.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.dux.organizer.MainActivity
import dev.dux.organizer.R
import dev.dux.organizer.data.Task
import dev.dux.organizer.data.TaskDao
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Traduce [reminderAction] a alarmas y notificaciones de Android.
 * Una alarma por tarea, identificada por su id: programar de nuevo reemplaza la anterior (sin duplicados).
 */
class Reminders(private val context: Context, private val dao: TaskDao) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Aplica la regla al estado actual de la tarea. */
    suspend fun sync(task: Task) {
        // Zona leída en cada llamada: tras un cambio de zona horaria, "las 9:00" se recalculan.
        when (val action = reminderAction(task, LocalDateTime.now())) {
            ReminderAction.Cancel -> cancel(task.id)
            is ReminderAction.Schedule -> schedule(task.id, action.at)
            ReminderAction.NotifyNow -> {
                notify(task)
                // Ya avisó: se borra para que un reinicio no lo repita.
                task.reminderAt?.let { dao.clearReminder(task.id, it) }
            }
        }
    }

    /** La alarma sonó: se vuelve a leer la tarea, porque pudo cambiar o borrarse desde que se programó. */
    suspend fun onAlarm(taskId: String) {
        dao.get(taskId)?.let { sync(it) }
    }

    /** Tras reinicio, cambio de hora, actualización de la app o restaurar un respaldo. */
    suspend fun syncAll() {
        dao.withReminder().forEach { sync(it) }
    }

    fun cancel(taskId: String) = alarms.cancel(alarmIntent(taskId))

    // Lint solo conoce SCHEDULE_EXACT_ALARM; usamos USE_EXACT_ALARM y comprobamos canScheduleExactAlarms() antes.
    @SuppressLint("MissingPermission")
    private fun schedule(taskId: String, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // USE_EXACT_ALARM se concede al instalar; el respaldo inexacto es por si algún día se revoca.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent(taskId))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent(taskId))
        }
    }

    private fun alarmIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        // El id va en el URI: así cada tarea tiene su propio PendingIntent y cancelar/reemplazar es exacto.
        Intent(context, ReminderReceiver::class.java).setData("jarvis://reminder/$taskId".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(task: Task) {
        val manager = NotificationManagerCompat.from(context)
        // Sin permiso o con las notificaciones apagadas no hay forma de avisar; el recordatorio se da por consumido.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData("jarvis://task/${task.id}".toUri())
                .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasks)
            .setContentTitle(task.title)
            .setContentText(task.notes ?: context.getString(R.string.reminder_default_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(task.id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}
