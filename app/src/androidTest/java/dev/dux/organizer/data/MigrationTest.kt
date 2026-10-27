package dev.dux.organizer.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Every migration is tested with data written in the old schema (app/schemas/<version>.json). */
class MigrationTest {
    private val dbName = "migration-test.db"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test fun v1ToV2KeepsDataAndAddsEmptyReminder() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO projects VALUES ('p1', 'Pro-Hielo', NULL, 'ACTIVE', 0, 0, 0)")
            // priority 3 = HIGH; dueDate in epoch days.
            execSQL(
                "INSERT INTO tasks (id, title, notes, status, priority, projectId, dueDate, scheduledDate, createdAt, updatedAt, completedAt) " +
                    "VALUES ('t1', 'Tarea vieja', 'nota', 'TODO', 3, 'p1', ${LocalDate.of(2026, 11, 1).toEpochDay()}, NULL, 0, 0, NULL)"
            )
            close()
        }

        // Checks that the resulting schema is identical to app/schemas/2.json.
        helper.runMigrationsAndValidate(dbName, 2, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val task = runBlocking { db.tasks().get("t1") }!!
        db.close()
        assertEquals("Tarea vieja", task.title)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals("p1", task.projectId)
        assertEquals(LocalDate.of(2026, 11, 1), task.dueDate)
        assertNull(task.reminderAt)
    }
}
