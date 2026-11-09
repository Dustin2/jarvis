package dev.dux.organizer.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SpotWhenTest {
    // Saturday 7 November 2026, 9:00.
    private val now = LocalDateTime.of(2026, 11, 7, 9, 0)
    private val today = now.toLocalDate()

    @Test fun dateAndTime() {
        assertEquals(
            Spotted("Llamar al dentista", today.plusDays(1), today.plusDays(1).atTime(10, 0)),
            spotWhen("Llamar al dentista mañana a las 10", now),
        )
    }

    @Test fun weekdayWithHangingConnector() {
        assertEquals(Spotted("Pagar la luz", LocalDate.of(2026, 11, 13), null), spotWhen("Pagar la luz para el viernes", now))
    }

    @Test fun timeOnlyIsTheNextOneAhead() {
        assertEquals(Spotted("Revisar correo", null, today.atTime(17, 0)), spotWhen("Revisar correo a las 5", now))
    }

    @Test fun inSomeDaysAndEnglish() {
        assertEquals(Spotted("Enviar factura", today.plusDays(3), null), spotWhen("Enviar factura en 3 días", now))
        assertEquals(Spotted("Call mom", today.plusDays(1), today.plusDays(1).atTime(18, 30)), spotWhen("Call mom tomorrow at 6:30 pm", now))
    }

    @Test fun monthNames() {
        val nov15 = LocalDate.of(2026, 11, 15)
        assertEquals(Spotted("Pagar renta", nov15, null), spotWhen("Pagar renta el 15 de noviembre", now))
        assertEquals(Spotted("Cita", nov15, nov15.atTime(10, 0)), spotWhen("Cita 15 nov a las 10", now))
        assertEquals(Spotted("Renew passport", LocalDate.of(2027, 3, 2), null), spotWhen("Renew passport on March 2nd", now))
        assertNull(spotWhen("Comprar 30 de febrero", now)) // no such date
    }

    @Test fun handPickedDateWinsForTheTime() {
        // At 9:00 "a las 8" is 20:00 today when today was picked, not 8:00 tomorrow.
        assertEquals(Spotted("Correr", null, today.atTime(20, 0)), spotWhen("Correr a las 8", now, due = today))
        assertNull(spotWhen("Correr a las 8 am", now, due = today)) // already gone on the picked day
    }

    @Test fun nothingToSpot() {
        assertNull(spotWhen("Comprar 2 leches", now))
        assertNull(spotWhen("Comprar 5 kilos de arroz", now))
        assertNull(spotWhen("Mañana", now))          // the title would be empty
        assertNull(spotWhen("Revisar lo de ayer", now)) // the past is not a due date
    }
}
