package dev.dux.organizer.reminders

import android.Manifest
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.dux.organizer.R
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.FinanceRepository
import dev.dux.organizer.data.TaskRepository
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
}
