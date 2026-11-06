package dev.dux.organizer.ask

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.Frequency
import dev.dux.organizer.data.MoneyKind
import dev.dux.organizer.data.Priority
import dev.dux.organizer.data.Recurrence
import java.time.DayOfWeek
import dev.dux.organizer.data.ShoppingItem
import dev.dux.organizer.data.ShoppingList
import dev.dux.organizer.data.Task
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionsTest {
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val today = LocalDate.of(2026, 10, 29) // Thursday
    private val super_ = ShoppingList(id = "s", name = "Súper", createdAt = now, updatedAt = now)
    private val hardware = ShoppingList(id = "f", name = "Ferretería", createdAt = now, updatedAt = now)
    private val lists = listOf(super_, hardware)
    private val internet = Task(title = "Pagar el internet", createdAt = now, updatedAt = now)
    private val invoices = Task(title = "Revisar facturas de clientes", createdAt = now, updatedAt = now)
    private val open = listOf(internet, invoices)

    private fun parse(output: String, command: String = "") = parseAction(output, command, open, lists, today)

    @Test
    fun addsShoppingToTheNamedListIgnoringAccents() {
        assertEquals(
            JarvisAction.AddShopping(hardware, listOf("Clavos"), null),
            parse("""{"accion":"agregar_compra","articulo":"clavos","cantidad":null,"lista":"ferreteria"}"""),
        )
    }

    @Test
    fun acceptsTheListNameInsideALongerText() {
        assertEquals(
            JarvisAction.AddShopping(super_, listOf("Tomate"), "2 kilos"),
            parse("""{"accion":"agregar_compra","articulo":"tomate","cantidad":"2 kilos","lista":"lista de compras Súper"}""", "Agrega 2 kilos de tomate al super"),
        )
    }

    @Test
    fun keepsOnlyQuantitiesTheUserSaid() {
        assertEquals(
            JarvisAction.AddShopping(super_, listOf("Tomate"), "2 kg"),
            parse("""{"accion":"agregar_compra","articulo":"tomate","cantidad":"2 kg","lista":"Súper"}""", "Apunta 2 kilos de tomate"),
        )
        // "Leche x2" is in the data, not in what the user said.
        assertEquals(
            JarvisAction.AddShopping(super_, listOf("Leche"), null),
            parse("""{"accion":"agregar_compra","articulo":"Leche","cantidad":"2","lista":"Súper"}""", "Agrega leche al súper"),
        )
    }

    @Test
    fun unknownListOrTaskGivesNoAction() {
        assertNull(parse("""{"accion":"agregar_compra","articulo":"pan","lista":"Panadería"}"""))
        assertNull(parse("""{"accion":"completar_tarea","tarea":"Lavar el carro"}"""))
    }

    @Test
    fun completesTheMatchingOpenTask() {
        assertEquals(JarvisAction.CompleteTask(internet), parse("""{"accion":"completar_tarea","tarea":"pagar el internet"}"""))
        assertEquals(JarvisAction.CompleteTask(invoices), parse("""{"accion":"completar_tarea","tarea":"facturas"}"""))
    }

    @Test
    fun createsTaskWithDateWorkedOutInCode() {
        assertEquals(
            JarvisAction.CreateTask("Llamar a Juan", today.plusDays(1)),
            parse("""Claro: {"accion":"crear_tarea","titulo":"llamar a Juan","vence":"mañana"}"""),
        )
        assertEquals(JarvisAction.CreateTask("Lavar el carro", null), parse("""{"accion":"crear_tarea","titulo":"Lavar el carro","vence":null}"""))
    }

    @Test
    fun repeatRules() {
        assertEquals(Recurrence(Frequency.DAILY), repeatRule("diario", today))
        assertEquals(Recurrence(Frequency.DAILY, 2), repeatRule("cada 2 días", today))
        assertEquals(Recurrence(Frequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), repeatRule("cada lunes y jueves", today))
        assertEquals(Recurrence(Frequency.WEEKLY, weekdays = setOf(today.dayOfWeek)), repeatRule("every week", today))
        assertEquals(Recurrence(Frequency.WEEKLY, 2, weekdays = setOf(DayOfWeek.FRIDAY)), repeatRule("every 2 weeks on Friday", today))
        assertEquals(Recurrence(Frequency.MONTHLY, monthDay = 1), repeatRule("cada mes el día 1", today))
        assertEquals(Recurrence(Frequency.MONTHLY, monthDay = 15), repeatRule("every month on the 15th", today))
        assertEquals(Recurrence(Frequency.MONTHLY, monthDay = today.dayOfMonth), repeatRule("mensual", today))
        assertNull(repeatRule("a veces", today))
    }

    @Test
    fun editsOnlyWhatWasSaid() {
        val nine = today.atTime(9, 0)
        fun parse(json: String) = parseAction(json, "", open, lists, today, now = nine)
        assertEquals(
            JarvisAction.EditTask(invoices, due = LocalDate.of(2026, 10, 30), priority = Priority.HIGH),
            parse("""{"accion":"editar_tarea","tarea":"facturas","vence":"el viernes","hora":null,"prioridad":"alta","titulo_nuevo":null,"repite":null}"""),
        )
        assertEquals(
            JarvisAction.EditTask(internet, remindAt = today.atTime(10, 0)),
            parse("""{"accion":"editar_tarea","tarea":"Pagar el internet","hora":"a las 10"}"""),
        )
        // Nothing to change, or an unknown task: no proposal.
        assertNull(parse("""{"accion":"editar_tarea","tarea":"Pagar el internet"}"""))
        assertNull(parse("""{"accion":"editar_tarea","tarea":"Lavar el carro","prioridad":"alta"}"""))
    }

    @Test
    fun aReminderTimeAloneOnAPastOrTodaysDueDateRollsForward() {
        val six = today.atTime(18, 0)
        fun parse(task: Task, json: String) = parseAction(json, "", listOf(task), lists, today, now = six)
        // Overdue since yesterday: "a las 5" is not yesterday at 5 (already gone) but tomorrow at 5 am.
        val overdue = internet.copy(dueDate = today.minusDays(1))
        assertEquals(
            JarvisAction.EditTask(overdue, remindAt = today.plusDays(1).atTime(5, 0)),
            parse(overdue, """{"accion":"editar_tarea","tarea":"Pagar el internet","hora":"a las 5"}"""),
        )
        // Due today and 5 pm has passed: same.
        val dueToday = internet.copy(dueDate = today)
        assertEquals(
            JarvisAction.EditTask(dueToday, remindAt = today.plusDays(1).atTime(5, 0)),
            parse(dueToday, """{"accion":"editar_tarea","tarea":"Pagar el internet","hora":"a las 5"}"""),
        )
        // Due in the future: the time is on that day, as before.
        val later = internet.copy(dueDate = today.plusDays(3))
        assertEquals(
            JarvisAction.EditTask(later, remindAt = today.plusDays(3).atTime(5, 0)),
            parse(later, """{"accion":"editar_tarea","tarea":"Pagar el internet","hora":"a las 5"}"""),
        )
    }

    @Test
    fun creatingAnExistingTaskEditsIt() {
        assertEquals(
            JarvisAction.EditTask(internet, due = LocalDate.of(2026, 11, 1), repeat = Recurrence(Frequency.MONTHLY, monthDay = 1)),
            parse("""{"accion":"crear_tarea","titulo":"Pagar el internet","vence":"el 1","repite":"cada mes"}"""),
        )
    }

    @Test
    fun createsRepeatingTask() {
        assertEquals(
            JarvisAction.CreateTask("Sacar la basura", null, null, Recurrence(Frequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY))),
            parse("""{"accion":"crear_tarea","titulo":"sacar la basura","vence":null,"repite":"cada lunes"}"""),
        )
    }

    @Test
    fun reminderTimes() {
        val nine = today.atTime(9, 0)
        assertEquals(today.atTime(17, 0), reminderTime("a las 5", null, nine)) // 5 a.m. already gone → 5 p.m.
        assertEquals(today.atTime(10, 0), reminderTime("a las 10", null, nine)) // 10 a.m. still ahead
        assertEquals(today.atTime(17, 30), reminderTime("17:30", null, nine))
        assertEquals(today.atTime(17, 0), reminderTime("5 de la tarde", null, nine))
        assertEquals(today.plusDays(1).atTime(8, 0), reminderTime("8 am", null, nine)) // gone today → tomorrow
        assertEquals(today.plusDays(1).atTime(5, 0), reminderTime("a las 5", today.plusDays(1), nine)) // other day: first one
        assertEquals(nine.plusHours(1), reminderTime("en una hora", null, nine))
        assertEquals(nine.plusMinutes(30), reminderTime("in 30 minutes", null, nine))
        assertEquals(today.atTime(12, 0), reminderTime("mediodía", null, nine))
        assertNull(reminderTime("8 am", today, nine)) // that day, already past
        assertNull(reminderTime("a las 25", null, nine))
        assertNull(reminderTime("luego", null, nine))
    }

    @Test
    fun createsTaskWithReminder() {
        val nine = today.atTime(9, 0)
        fun parse(json: String) = parseAction(json, "", open, lists, today, now = nine)
        assertEquals(
            JarvisAction.CreateTask("Llamar a Juan", today, today.atTime(17, 0)),
            parse("""{"accion":"crear_tarea","titulo":"llamar a Juan","vence":null,"hora":"a las 5"}"""),
        )
        // Time inside "vence".
        assertEquals(
            JarvisAction.CreateTask("Pagar la luz", today.plusDays(1), today.plusDays(1).atTime(10, 0)),
            parse("""{"accion":"crear_tarea","titulo":"pagar la luz","vence":"mañana a las 10"}"""),
        )
        // A time where the date should be.
        assertEquals(
            JarvisAction.CreateTask("Call John", today, nine.plusHours(2)),
            parse("""{"accion":"crear_tarea","titulo":"call John","vence":"in 2 hours"}"""),
        )
    }

    @Test
    fun relativeDates() {
        assertEquals(today, relativeDate("hoy", today))
        assertEquals(today.plusDays(2), relativeDate("Pasado mañana", today))
        assertEquals(LocalDate.of(2026, 10, 30), relativeDate("el viernes", today))
        assertEquals(LocalDate.of(2026, 11, 5), relativeDate("jueves", today)) // next week, not today
        assertEquals(LocalDate.of(2026, 11, 2), relativeDate("2026-11-02", today))
        assertNull(relativeDate("29-10-2026", today))
        assertEquals(today.plusDays(1), relativeDate("Tomorrow", today))
        assertEquals(today.plusDays(3), relativeDate("en 3 días", today))
        assertEquals(today.plusWeeks(2), relativeDate("in 2 weeks", today))
        assertEquals(today.plusDays(1), relativeDate("en un día", today))
        assertEquals(today.plusWeeks(1), relativeDate("la próxima semana", today))
        assertEquals(LocalDate.of(2026, 11, 15), relativeDate("el 15", today)) // already past in October → November
        assertEquals(LocalDate.of(2026, 10, 31), relativeDate("on the 31st", today))
        assertEquals(LocalDate.of(2026, 12, 31), relativeDate("el 31", LocalDate.of(2026, 11, 1))) // November has no 31
        assertEquals(LocalDate.of(2026, 10, 22), relativeDate("el jueves", today, past = true)) // last Thursday, not today
        assertNull(relativeDate("el 32", today))
        assertNull(relativeDate("pronto", today))
        assertEquals(LocalDate.of(2026, 10, 30), relativeDate("on Friday", today))
    }

    private val food = Category(name = "Comida", kind = MoneyKind.EXPENSE, createdAt = now, updatedAt = now)
    private val salary = Category(name = "Sueldo", kind = MoneyKind.INCOME, createdAt = now, updatedAt = now)
    private val oldCategory = Category(name = "Gimnasio", kind = MoneyKind.EXPENSE, archived = true, createdAt = now, updatedAt = now)
    private fun money(output: String, command: String) = parseAction(output, command, open, lists, today, listOf(food, salary, oldCategory))

    @Test
    fun recordsMovementsTheUserSaid() {
        assertEquals(
            JarvisAction.RecordMovement(food, 250_00, "tacos", today),
            money("""{"accion":"registrar_movimiento","monto":"250","categoria":"comida","nota":"tacos"}""", "Gasté 250 en tacos"),
        )
        assertEquals(
            JarvisAction.RecordMovement(salary, 5_000_00, null, today),
            money("""{"accion":"registrar_movimiento","monto":"5000","categoria":"Sueldo","nota":null}""", "Me pagaron 5,000 de sueldo"),
        )
    }

    @Test
    fun movementsUseThePastDateTheUserSaid() {
        fun dated(fecha: String) = money("""{"accion":"registrar_movimiento","monto":"200","categoria":"Comida","fecha":"$fecha"}""", "Gasté 200 en comida")
        assertEquals(today.minusDays(1), (dated("ayer") as JarvisAction.RecordMovement).date)
        assertEquals(LocalDate.of(2026, 10, 26), (dated("el lunes") as JarvisAction.RecordMovement).date) // last Monday
        assertEquals(LocalDate.of(2026, 10, 15), (dated("el 15") as JarvisAction.RecordMovement).date)
        assertEquals(today, (dated("mañana") as JarvisAction.RecordMovement).date) // not spent yet: today, not a future date
    }

    @Test
    fun madeUpAmountsAndUnknownCategoriesGiveNoAction() {
        // 5 is not 500: only an amount the user actually said counts.
        assertNull(money("""{"accion":"registrar_movimiento","monto":"5","categoria":"Comida"}""", "Gasté 500 en comida"))
        assertNull(money("""{"accion":"registrar_movimiento","monto":"250","categoria":"Viajes"}""", "Gasté 250 en el viaje"))
        assertNull(money("""{"accion":"registrar_movimiento","monto":"250","categoria":"Gimnasio"}""", "Pagué 250 del gimnasio"))
    }

    @Test
    fun noneAndGarbageGiveNoAction() {
        assertNull(parse("""{"accion":"ninguna"}"""))
        assertNull(parse("No entendí"))
    }

    @Test
    fun addsSeveralItemsFromOneCommand() {
        assertEquals(
            JarvisAction.AddShopping(super_, listOf("Leche", "Pan", "Huevos"), null),
            parse("""{"accion":"agregar_compra","articulo":"leche, pan y huevos","cantidad":"2","lista":"Súper"}""", "agrega 2 leche, pan y huevos"),
        )
        assertEquals(listOf("Pan y mantequilla"), splitItems("Pan y mantequilla"))
        assertEquals(listOf("eggs", "bread", "milk"), splitItems("eggs, bread, and milk"))
    }

    private val milk = ShoppingItem(id = "m", listId = "s", name = "Leche", createdAt = now, updatedAt = now)
    private val nails = ShoppingItem(id = "n", listId = "f", name = "Clavos", createdAt = now, updatedAt = now)
    private val projects = mapOf("p1" to "Personal", "p2" to "Pro-Hielo")
    private fun parseMore(output: String) = parseAction(output, "", open, lists, today, items = listOf(milk, nails), projects = projects)

    @Test
    fun removesAnExistingItemOnly() {
        assertEquals(JarvisAction.RemoveShopping(super_, milk), parseMore("""{"accion":"quitar_compra","articulo":"leche","lista":null}"""))
        assertNull(parseMore("""{"accion":"quitar_compra","articulo":"leche","lista":"Ferretería"}"""))
        assertNull(parseMore("""{"accion":"quitar_compra","articulo":"queso","lista":null}"""))
    }

    @Test
    fun deletesAndMovesOnlyKnownTasksAndProjects() {
        assertEquals(JarvisAction.DeleteTask(internet), parseMore("""{"accion":"borrar_tarea","tarea":"pagar el internet"}"""))
        assertNull(parseMore("""{"accion":"borrar_tarea","tarea":"lavar el carro"}"""))
        assertEquals(JarvisAction.MoveTask(invoices, "p2", "Pro-Hielo"), parseMore("""{"accion":"mover_tarea","tarea":"Revisar facturas","proyecto":"proyecto pro-hielo"}"""))
        assertNull(parseMore("""{"accion":"mover_tarea","tarea":"Revisar facturas","proyecto":"Casa"}"""))
    }
}
