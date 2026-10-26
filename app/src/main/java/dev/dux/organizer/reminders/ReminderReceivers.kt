package dev.dux.organizer.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.dux.organizer.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** goAsync: leer la base toma más que lo que Android permite dentro de onReceive. */
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
        runAsync { context.app.reminders.onAlarm(taskId) }
    }
}

/** Android borra las alarmas al reiniciar, y un cambio de hora o zona cambia cuándo son "las 9:00". */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        runAsync { context.app.reminders.syncAll() }
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
