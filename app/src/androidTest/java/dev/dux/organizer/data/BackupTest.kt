package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val dir = File(context.cacheDir, "backup-test").apply { deleteRecursively() }
    private val backups = BackupRepository(db, dir)
    private val projects = ProjectRepository(db.projects())
    private val tasks = TaskRepository(db.tasks())
    private val shopping = ShoppingRepository(db.shopping())
    private val finance = FinanceRepository(db.finance())

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    /** One project, two tasks, a shopping list and finances covering every kind of field (dates, nulls, project). */
    private suspend fun sampleData(prefix: String = "") {
        projects.create("${prefix}Pro-Hielo")
        val projectId = projects.visible.first().first().id
        tasks.capture("${prefix}Con todo", "notas")
        tasks.capture("${prefix}Mínima")
        val full = tasks.inbox.first().first { it.title == "${prefix}Con todo" }.id
        tasks.setProject(full, projectId)
        tasks.setDueDate(full, LocalDate.of(2026, 10, 31))
        tasks.setScheduledDate(full, LocalDate.of(2026, 10, 28))
        tasks.setPriority(full, Priority.HIGH)
        tasks.setReminder(full, LocalDateTime.of(2026, 10, 30, 9, 15))
        tasks.setRecurrence(full, Recurrence(Frequency.MONTHLY, monthDay = 31))
        tasks.setStatus(full, TaskStatus.DONE)
        val listId = shopping.createList("${prefix}Súper")!!
        shopping.addItem(listId, "${prefix}Leche")
        shopping.addItem(listId, "${prefix}Tortillas", "2 kg")
        shopping.setChecked(db.shopping().getAllItems().first().id, true)
        finance.createCategory("${prefix}Comida", MoneyKind.EXPENSE, 300_000)
        finance.createCategory("${prefix}Sueldo", MoneyKind.INCOME, null)
        val categories = db.finance().getAllCategories()
        finance.addMovement(8550, categories.first { it.kind == MoneyKind.EXPENSE }.id, LocalDate.of(2026, 10, 27), "${prefix}tacos")
        finance.addMovement(1_500_000, categories.first { it.kind == MoneyKind.INCOME }.id, LocalDate.of(2026, 10, 15))
        finance.addDebt("${prefix}Juan", DebtDirection.OWED_TO_ME, 100_000, LocalDate.of(2026, 11, 30), "préstamo")
        finance.addPayment(db.finance().getAllDebts().single().id, 40_000, LocalDate.of(2026, 10, 27))
    }

    @Test fun exportThenDecodeGivesTheSameData() = runBlocking {
        sampleData()
        val decoded = decodeBackup(backups.export())
        assertEquals(db.projects().getAll(), decoded.projects)
        assertEquals(db.tasks().getAll(), decoded.tasks)
        assertEquals(db.shopping().getAllLists(), decoded.shoppingLists)
        assertEquals(db.shopping().getAllItems(), decoded.shoppingItems)
        assertEquals(db.finance().getAllCategories(), decoded.categories)
        assertEquals(db.finance().getAllMovements(), decoded.movements)
        assertEquals(db.finance().getAllDebts(), decoded.debts)
        assertEquals(db.finance().getAllPayments(), decoded.debtPayments)
    }

    @Test fun restoreReplacesEverythingAndUndoBringsItBack() = runBlocking {
        sampleData("A-")
        val fileA = backups.export()
        db.tasks().deleteAll(); db.projects().deleteAll(); db.shopping().deleteAllLists(); db.finance().deleteAll()
        sampleData("B-")
        val before = db.tasks().getAll()
        val itemsBefore = db.shopping().getAllItems()
        val movementsBefore = db.finance().getAllMovements()

        backups.restore(decodeBackup(fileA))
        assertTrue(db.tasks().getAll().all { it.title.startsWith("A-") })
        assertTrue(db.shopping().getAllItems().all { it.name.startsWith("A-") })
        assertTrue(db.finance().getAllMovements().all { it.note == null || it.note!!.startsWith("A-") })
        assertEquals("A-Juan", db.finance().getAllDebts().single().person)
        assertTrue(backups.canUndoRestore)

        backups.undoRestore()
        assertEquals(before, db.tasks().getAll())
        assertEquals(itemsBefore, db.shopping().getAllItems())
        assertEquals(movementsBefore, db.finance().getAllMovements())
        assertFalse(backups.canUndoRestore)
    }

    @Test fun invalidFilesAreRejectedWithReason() = runBlocking {
        sampleData()
        val valid = backups.export()
        fun broken(change: JSONObject.() -> Unit) = JSONObject(valid).apply(change).toString()
        fun task(o: JSONObject) = o.getJSONArray("tasks").getJSONObject(0)

        val cases = mapOf(
            "not json" to "hola",
            "other format" to broken { put("format", "otra.app") },
            "newer version" to broken { put("version", BACKUP_VERSION + 1) },
            "invalid enum" to broken { task(this).put("status", "ARCHIVADA") },
            "invalid date" to broken { task(this).put("dueDate", "31/10/2026") },
            "blank title" to broken { task(this).put("title", "   ") },
            "missing project" to broken { task(this).put("projectId", "no-existe") },
            "duplicate id" to broken { getJSONArray("tasks").put(JSONObject(task(this).toString())) },
            "missing list" to broken { remove("projects") },
            "invalid rule" to broken { task(this).put("recurrence", "FREQ=YEARLY") },
            "missing shopping list" to broken { getJSONArray("shoppingItems").getJSONObject(0).put("listId", "no-existe") },
            "checked not boolean" to broken { getJSONArray("shoppingItems").getJSONObject(0).put("checked", "sí") },
            "amount with decimals" to broken { getJSONArray("movements").getJSONObject(0).put("amountCents", 85.5) },
            "negative amount" to broken { getJSONArray("movements").getJSONObject(0).put("amountCents", -100) },
            "missing category" to broken { getJSONArray("movements").getJSONObject(0).put("categoryId", "no-existe") },
            "kind mismatch" to broken {
                val m = getJSONArray("movements").getJSONObject(0)
                m.put("kind", if (m.getString("kind") == "INCOME") "EXPENSE" else "INCOME")
            },
            "income budget" to broken { getJSONArray("categories").getJSONObject(1).put("monthlyBudgetCents", 100) },
            "missing debt" to broken { getJSONArray("debtPayments").getJSONObject(0).put("debtId", "no-existe") },
        )
        cases.forEach { (case, text) ->
            val error = assertThrows(case, InvalidBackupException::class.java) { decodeBackup(text) }
            assertTrue("$case: no message", !error.message.isNullOrBlank())
        }
    }

    @Test fun olderOrMissingOptionalFieldsAreAccepted() {
        val minimal = """
            {"format":"$BACKUP_FORMAT","version":1,"projects":[],
             "tasks":[{"id":"t1","title":"Solo lo básico","status":"INBOX","priority":"NONE",
                       "createdAt":"2026-10-25T10:00:00Z","updatedAt":"2026-10-25T10:00:00Z"}]}
        """.trimIndent()
        val task = decodeBackup(minimal).tasks.single()
        assertEquals("Solo lo básico", task.title)
        assertEquals(null, task.dueDate)
        assertEquals(null, task.projectId)
        assertEquals(null, task.reminderAt)
        assertEquals(emptyList<ShoppingList>(), decodeBackup(minimal).shoppingLists)
        assertEquals(emptyList<Movement>(), decodeBackup(minimal).movements)
    }

    @Test fun reminderSurvivesTheRoundTrip() = runBlocking {
        sampleData()
        val task = decodeBackup(backups.export()).tasks.first { it.title == "Con todo" }
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 15), task.reminderAt)
        assertEquals(Recurrence(Frequency.MONTHLY, monthDay = 31, reminderTime = java.time.LocalTime.of(9, 15)), task.recurrence)
        assertEquals(task.id, task.seriesId)
    }
}
