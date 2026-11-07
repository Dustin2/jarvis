package dev.dux.organizer.reminders

import android.Manifest
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.dux.organizer.R
import dev.dux.organizer.data.AppDatabase
import dev.dux.organizer.data.TaskRepository
import dev.dux.organizer.data.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val reminders = Reminders(context, db.tasks())
    private val tasks = TaskRepository(db.tasks(), reminders = reminders)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @After fun tearDown() {
        notifications.cancelAll()
        db.close()
    }

    @Test fun aDueReminderOffersCompleteAndPostponeAndCompletingClosesIt() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        reminders.createChannel()
        tasks.capture("Pagar la luz", id = "t")
        // A minute ago: the rule notifies right away.
        tasks.setReminder("t", LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0))

        val shown = notifications.activeNotifications.single { it.id == "t".hashCode() }.notification
        assertEquals(listOf(context.getString(R.string.complete), context.getString(R.string.postpone)), shown.actions.map { it.title.toString() })

        reminders.completeFromNotification("t", tasks)
        assertEquals(TaskStatus.DONE, tasks.observe("t").first()!!.status)
        assertTrue(notifications.activeNotifications.none { it.id == "t".hashCode() })
    }
}
