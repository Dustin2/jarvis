package dev.dux.organizer.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.dux.organizer.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** goAsync: reading the database takes longer than Android allows inside onReceive. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.data?.lastPathSegment ?: return
        runAsync {
            val app = context.app
            if (intent.action == Reminders.ACTION_COMPLETE) app.reminders.completeFromNotification(taskId, app.tasks) else app.reminders.onAlarm(taskId)
        }
    }
}

class MorningBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        if (intent.action == MorningBrief.ACTION_WEEKLY) context.app.morningBrief.onWeeklyAlarm() else context.app.morningBrief.onAlarm()
    }
}

/** Android clears alarms on reboot, and a time or time zone change moves when "9:00" is. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        runAsync {
            context.app.reminders.syncAll()
            context.app.morningBrief.schedule()
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
