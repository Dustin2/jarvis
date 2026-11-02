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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dux.organizer.MainActivity
import dev.dux.organizer.R
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.finances.monthSummary
import dev.dux.organizer.today.planDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.briefStore by preferencesDataStore("morning_brief")
private val ENABLED = booleanPreferencesKey("enabled")
private val MINUTE_OF_DAY = intPreferencesKey("minuteOfDay")
private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

data class BriefSettings(val enabled: Boolean, val time: LocalTime)

/** The next time the brief should show: today at [time] if it is still ahead, otherwise tomorrow. */
fun nextBriefAt(now: LocalDateTime, time: LocalTime): LocalDateTime =
    now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }

/**
 * A daily notification with what is overdue, what is due today and the month's spending. Its text is
 * worked out here, without the model: loading 2 GB in the background every morning would cost battery.
 * Tapping it opens Pregúntale a Jarvis, which reads the day aloud.
 */
class MorningBrief(
    private val context: Context,
    private val tasks: TaskRepository,
    private val finance: FinanceRepository,
) {
    private val store = context.briefStore
    private val alarms = context.getSystemService(AlarmManager::class.java)

    val settings: Flow<BriefSettings> = store.data.map { p ->
        BriefSettings(p[ENABLED] ?: false, p[MINUTE_OF_DAY]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: DEFAULT_TIME)
    }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.brief_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
        schedule()
    }

    suspend fun setTime(time: LocalTime) {
        store.edit { it[MINUTE_OF_DAY] = time.hour * 60 + time.minute }
        schedule()
    }

    /** Sets (or clears) the next alarm from the saved settings; on start, after a reboot or a time change. */
    suspend fun schedule() {
        val s = settings.first()
        if (!s.enabled) return alarms.cancel(alarmIntent())
        val millis = nextBriefAt(LocalDateTime.now(), s.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(millis)
    }

    /** The alarm fired: show today's brief and set tomorrow's. */
    suspend fun onAlarm() {
        if (!settings.first().enabled) return
        notify(briefText(LocalDate.now()))
        schedule()
    }

    private suspend fun briefText(today: LocalDate): String {
        val res = context.resources
        val plan = planDay(tasks.open.first(), today)
        val parts = mutableListOf<String>()
        if (plan.overdue.isNotEmpty()) parts += res.getQuantityString(R.plurals.overdue_count, plan.overdue.size, plan.overdue.size)
        parts += if (plan.today.isEmpty()) res.getString(R.string.empty_today)
        else res.getQuantityString(R.plurals.brief_due_today, plan.today.size, plan.today.size)
        val month = monthSummary(finance.categories.first(), finance.movements(YearMonth.from(today)).first())
        parts += month.budgetCents?.let { res.getString(R.string.spent_of_budget_total, formatMoney(month.expenseCents), formatMoney(it)) }
            ?: res.getString(R.string.month_spent, formatMoney(month.expenseCents))
        return parts.joinToString(" · ")
    }

    // Same exact-alarm approach as the reminders (USE_EXACT_ALARM, inexact fallback).
    @SuppressLint("MissingPermission")
    private fun setAlarm(millis: Long) {
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent())
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, MorningBriefReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(text: String) {
        val manager = NotificationManagerCompat.from(context)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_BRIEF)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(context.getString(R.string.brief_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + context.getString(R.string.brief_tap)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "morning_brief"
        const val NOTIFICATION_ID = 1
    }
}
