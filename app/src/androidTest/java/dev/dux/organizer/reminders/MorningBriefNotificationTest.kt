package dev.dux.organizer.reminders

import android.Manifest
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.dux.organizer.R
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.formatMoney
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val brief = MorningBrief(context, TaskRepository(db.tasks()), FinanceRepository(db.finance()))
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @After fun tearDown() = runBlocking {
        brief.setEnabled(false) // also cancels the alarm the test set
        notifications.cancelAll()
        db.close()
    }

    @Test fun alarmShowsTheBriefWithTodaysCounts() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        brief.createChannel()
        TaskRepository(db.tasks()).capture("Pagar la luz", dueDate = java.time.LocalDate.now())
        brief.setEnabled(true)

        brief.onAlarm()

        val shown = notifications.activeNotifications.map { it.notification }
        val title = context.getString(R.string.brief_title)
        val dueToday = context.resources.getQuantityString(R.plurals.brief_due_today, 1, 1)
        assertTrue(shown.any { it.extras.getString("android.title") == title && it.extras.getCharSequence("android.text").toString().contains(dueToday) })
    }

    @Test fun weeklyAlarmShowsTheWeek() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        brief.createChannel()
        val tasks = TaskRepository(db.tasks())
        tasks.capture("Pagar la luz", id = "t")
        tasks.complete("t")
        val finance = FinanceRepository(db.finance())
        finance.seedIfEmpty()
        val food = finance.categories.first().first { it.kind == MoneyKind.EXPENSE }
        finance.addMovement(12_50, food.id, LocalDate.now())
        brief.setWeekly(true)

        brief.onWeeklyAlarm()

        val shown = notifications.activeNotifications.map { it.notification }
        val text = shown.single { it.extras.getString("android.title") == context.getString(R.string.weekly_title) }.extras.getCharSequence("android.text").toString()
        assertTrue(text, text.contains(context.resources.getQuantityString(R.plurals.weekly_done, 1, 1)))
        assertTrue(text, text.contains(context.getString(R.string.weekly_spent, formatMoney(12_50))))
        brief.setWeekly(false)
    }
}
