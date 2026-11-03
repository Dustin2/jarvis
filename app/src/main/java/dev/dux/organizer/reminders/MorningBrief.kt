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
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.TaskStatus
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.formatMoney
import dev.dux.organizer.finances.monthSummary
import dev.dux.organizer.today.planDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.briefStore by preferencesDataStore("morning_brief")
private val ENABLED = booleanPreferencesKey("enabled")
private val MINUTE_OF_DAY = intPreferencesKey("minuteOfDay")
private val WEEKLY = booleanPreferencesKey("weekly")
private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
private val WEEKLY_TIME: LocalTime = LocalTime.of(19, 0)

data class BriefSettings(val enabled: Boolean, val time: LocalTime, val weekly: Boolean = false)

/** The next time the brief should show: today at [time] if it is still ahead, otherwise tomorrow. */
fun nextBriefAt(now: LocalDateTime, time: LocalTime): LocalDateTime =
    now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }

/** The next Sunday at 19:00 still ahead: when the week's summary shows. */
fun nextWeeklyAt(now: LocalDateTime): LocalDateTime =
    now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(WEEKLY_TIME).let { if (it.isAfter(now)) it else it.plusWeeks(1) }

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
        BriefSettings(p[ENABLED] ?: false, p[MINUTE_OF_DAY]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: DEFAULT_TIME, p[WEEKLY] ?: false)
    }

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.brief_channel), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(WEEKLY_CHANNEL_ID, context.getString(R.string.weekly_channel), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    suspend fun setWeekly(enabled: Boolean) {
        store.edit { it[WEEKLY] = enabled }
        schedule()
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
        schedule()
    }

    suspend fun setTime(time: LocalTime) {
        store.edit { it[MINUTE_OF_DAY] = time.hour * 60 + time.minute }
        schedule()
    }

    /** Sets (or clears) the next alarms from the saved settings; on start, after a reboot or a time change. */
    suspend fun schedule() {
        val s = settings.first()
        val now = LocalDateTime.now()
        if (s.enabled) setAlarm(nextBriefAt(now, s.time), alarmIntent()) else alarms.cancel(alarmIntent())
        if (s.weekly) setAlarm(nextWeeklyAt(now), alarmIntent(weekly = true)) else alarms.cancel(alarmIntent(weekly = true))
    }

    /** The alarm fired: show today's brief and set tomorrow's. */
    suspend fun onAlarm() {
        if (!settings.first().enabled) return
        notify(briefText(LocalDate.now()))
        schedule()
    }

    /** Sunday evening: the week's summary, and next Sunday's alarm. */
    suspend fun onWeeklyAlarm() {
        if (!settings.first().weekly) return
        notify(weeklyText(LocalDate.now()), weekly = true)
        schedule()
    }

    /** Done since Monday, what is still open and the week's spending. */
    private suspend fun weeklyText(today: LocalDate): String {
        val res = context.resources
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val zone = ZoneId.systemDefault()
        val done = tasks.finished.first().count { t -> t.status == TaskStatus.DONE && t.completedAt?.atZone(zone)?.toLocalDate()?.let { it in monday..today } == true }
        val plan = planDay(tasks.open.first(), today)
        val months = setOf(YearMonth.from(monday), YearMonth.from(today))
        val spent = months.flatMap { finance.movements(it).first() }.filter { it.kind == MoneyKind.EXPENSE && it.date in monday..today }.sumOf { it.amountCents }
        return listOf(
            res.getQuantityString(R.plurals.weekly_done, done, done),
            res.getQuantityString(R.plurals.open_count, plan.openCount, plan.openCount) +
                if (plan.overdue.isEmpty()) "" else " (" + res.getQuantityString(R.plurals.overdue_count, plan.overdue.size, plan.overdue.size) + ")",
            res.getString(R.string.weekly_spent, formatMoney(spent)),
        ).joinToString(" · ")
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
    private fun setAlarm(at: LocalDateTime, intent: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }

    // Different request codes and actions: the two alarms must not replace each other.
    private fun alarmIntent(weekly: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (weekly) 1 else 0,
        Intent(context, MorningBriefReceiver::class.java).setAction(if (weekly) ACTION_WEEKLY else null),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The weekly summary just opens the app; the morning brief opens Jarvis to read the day aloud. */
    private fun notify(text: String, weekly: Boolean = false) {
        val manager = NotificationManagerCompat.from(context)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            if (weekly) 1 else 0,
            Intent(context, MainActivity::class.java)
                .setAction(if (weekly) null else MainActivity.ACTION_BRIEF)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, if (weekly) WEEKLY_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(context.getString(if (weekly) R.string.weekly_title else R.string.brief_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (weekly) text else text + "\n" + context.getString(R.string.brief_tap)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(if (weekly) WEEKLY_NOTIFICATION_ID else NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_WEEKLY = "dev.dux.organizer.WEEKLY_SUMMARY"
        private const val CHANNEL_ID = "morning_brief"
        private const val WEEKLY_CHANNEL_ID = "weekly_summary"
        private const val NOTIFICATION_ID = 1
        private const val WEEKLY_NOTIFICATION_ID = 2
    }
}
