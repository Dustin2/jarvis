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
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns [reminderAction] into Android alarms and notifications.
 * One alarm per task, identified by its id: scheduling again replaces the previous one (no duplicates).
 */
class Reminders(private val context: Context, private val dao: TaskDao) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Applies the rule to the task's current state. */
    suspend fun sync(task: Task) {
        // Zone read on every call: after a time zone change, "9:00" is recalculated.
        when (val action = reminderAction(task, LocalDateTime.now())) {
            ReminderAction.Cancel -> cancel(task.id)
            is ReminderAction.Schedule -> schedule(task.id, action.at)
            ReminderAction.NotifyNow -> {
                notify(task)
                // Already notified: cleared so a reboot does not repeat it.
                task.reminderAt?.let { dao.clearReminder(task.id, it) }
            }
        }
    }

    /** The alarm fired: the task is read again, since it may have changed or been deleted since scheduling. */
    suspend fun onAlarm(taskId: String) {
        dao.get(taskId)?.let { sync(it) }
    }

    /** After a reboot, time change, app update or backup restore. */
    suspend fun syncAll() {
        dao.withReminder().forEach { sync(it) }
    }

    fun cancel(taskId: String) = alarms.cancel(alarmIntent(taskId))

    // Lint only knows SCHEDULE_EXACT_ALARM; we use USE_EXACT_ALARM and check canScheduleExactAlarms() first.
    @SuppressLint("MissingPermission")
    private fun schedule(taskId: String, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // USE_EXACT_ALARM is granted at install; the inexact fallback is in case it is ever revoked.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent(taskId))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent(taskId))
        }
    }

    private fun alarmIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        // The id goes in the URI: each task gets its own PendingIntent, so cancel/replace is exact.
        Intent(context, ReminderReceiver::class.java).setData("jarvis://reminder/$taskId".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(task: Task) {
        val manager = NotificationManagerCompat.from(context)
        // Without permission or with notifications off there is no way to notify; the reminder counts as consumed.
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
            // Right from the notification: done, or a new date and time (Posponer opens the app to pick them).
            .addAction(R.drawable.ic_check, context.getString(R.string.complete), completeIntent(task.id))
            .addAction(R.drawable.ic_bell, context.getString(R.string.postpone), postponeIntent(task.id))
            .build()
        manager.notify(notificationId(task.id), notification)
    }

    /** Completed from the notification: same rules as in the app (a recurring task creates the next one). */
    suspend fun completeFromNotification(taskId: String, tasks: TaskRepository) {
        tasks.setStatus(taskId, TaskStatus.DONE)
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    fun dismissNotification(taskId: String) = NotificationManagerCompat.from(context).cancel(notificationId(taskId))

    // Own URIs: a PendingIntent per task and per button, distinct from the alarm's.
    private fun completeIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_COMPLETE).setData("jarvis://complete/$taskId".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun postponeIntent(taskId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_POSTPONE)
            .setData("jarvis://postpone/$taskId".toUri())
            .putExtra(MainActivity.EXTRA_POSTPONE_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notificationId(taskId: String) = taskId.hashCode()

    companion object {
        const val CHANNEL_ID = "reminders"
        const val ACTION_COMPLETE = "dev.dux.organizer.COMPLETE_TASK"
    }
}
