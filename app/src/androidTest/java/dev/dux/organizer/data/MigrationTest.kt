package dev.dux.organizer.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
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

    @Test fun v2ToV3KeepsDataAndAddsEmptyRecurrence() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO projects VALUES ('p1', 'Pro-Hielo', NULL, 'ACTIVE', 0, 0, 0)")
            execSQL(
                "INSERT INTO tasks (id, title, notes, status, priority, projectId, dueDate, scheduledDate, createdAt, updatedAt, completedAt, reminderAt) " +
                    "VALUES ('t1', 'Con recordatorio', NULL, 'TODO', 2, 'p1', NULL, NULL, 0, 0, NULL, '2026-11-01T09:00')"
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val task = runBlocking { db.tasks().get("t1") }!!
        db.close()
        assertEquals("Con recordatorio", task.title)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 1, 9, 0), task.reminderAt)
        assertNull(task.recurrence)
        assertNull(task.seriesId)
    }

    @Test fun v3ToV4KeepsTasksAndAddsEmptyShoppingTables() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO tasks (id, title, notes, status, priority, projectId, dueDate, scheduledDate, createdAt, updatedAt, completedAt, reminderAt, recurrence, seriesId) " +
                    "VALUES ('t1', 'Se repite', NULL, 'TODO', 0, NULL, NULL, ${LocalDate.of(2026, 11, 1).toEpochDay()}, 0, 0, NULL, NULL, 'FREQ=DAILY;INTERVAL=1', 't1')"
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 4, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val task = runBlocking { db.tasks().get("t1") }!!
        val lists = runBlocking { db.shopping().getAllLists() }
        db.close()
        assertEquals(Recurrence(Frequency.DAILY), task.recurrence)
        assertEquals(emptyList<ShoppingList>(), lists)
    }

    @Test fun v4ToV5KeepsShoppingAndAddsEmptyFinanceTables() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO shopping_lists (id, name, sortOrder, createdAt, updatedAt) VALUES ('l1', 'Súper', 0, 0, 0)")
            execSQL("INSERT INTO shopping_items (id, listId, name, quantity, checked, sortOrder, createdAt, updatedAt) VALUES ('i1', 'l1', 'Leche', '2', 1, 0, 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 5, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val items = runBlocking { db.shopping().getAllItems() }
        val categories = runBlocking { db.finance().getAllCategories() }
        db.close()
        assertEquals(listOf(ShoppingItem(id = "i1", listId = "l1", name = "Leche", quantity = "2", checked = true, createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH)), items)
        assertEquals(emptyList<Category>(), categories)
    }

    @Test fun v5ToV6KeepsTasksAndProjectsOutOfTheTrash() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO projects VALUES ('p1', 'Pro-Hielo', NULL, 'ACTIVE', 0, 0, 0)")
            execSQL(
                "INSERT INTO tasks (id, title, notes, status, priority, projectId, dueDate, scheduledDate, createdAt, updatedAt, completedAt, reminderAt, recurrence, seriesId) " +
                    "VALUES ('t1', 'Tarea', NULL, 'TODO', 0, 'p1', NULL, NULL, 0, 0, NULL, NULL, NULL, NULL)"
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val tasks = runBlocking { db.tasks().getAll() }
        val projects = runBlocking { db.projects().getAll() }
        db.close()
        assertEquals(listOf("t1"), tasks.map { it.id })
        assertNull(tasks.single().deletedAt)
        assertNull(projects.single().deletedAt)
    }

    @Test fun v6ToV7KeepsFinancesAndStartsWithAnEmptyTrash() {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO finance_debts (id, person, direction, amountCents, dueDate, note, settledAt, createdAt, updatedAt) VALUES ('d1', 'Ana', 'I_OWE', 10000, NULL, NULL, NULL, 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 7, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val debts = runBlocking { db.finance().getAllDebts() }
        val trashed = runBlocking { db.finance().observeTrashed().first() }
        db.close()
        assertEquals(listOf("Ana"), debts.map { it.person })
        assertTrue(trashed.isEmpty())
    }
}
