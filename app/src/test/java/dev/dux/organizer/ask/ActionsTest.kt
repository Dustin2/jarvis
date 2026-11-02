package dev.dux.organizer.ask

import dev.dux.organizer.data.Category
import dev.dux.organizer.data.MoneyKind
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
            JarvisAction.AddShopping(hardware, "Clavos", null),
            parse("""{"accion":"agregar_compra","articulo":"clavos","cantidad":null,"lista":"ferreteria"}"""),
        )
    }

    @Test
    fun acceptsTheListNameInsideALongerText() {
        assertEquals(
            JarvisAction.AddShopping(super_, "Tomate", "2 kilos"),
            parse("""{"accion":"agregar_compra","articulo":"tomate","cantidad":"2 kilos","lista":"lista de compras Súper"}""", "Agrega 2 kilos de tomate al super"),
        )
    }

    @Test
    fun keepsOnlyQuantitiesTheUserSaid() {
        assertEquals(
            JarvisAction.AddShopping(super_, "Tomate", "2 kg"),
            parse("""{"accion":"agregar_compra","articulo":"tomate","cantidad":"2 kg","lista":"Súper"}""", "Apunta 2 kilos de tomate"),
        )
        // "Leche x2" is in the data, not in what the user said.
        assertEquals(
            JarvisAction.AddShopping(super_, "Leche", null),
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
}
