package dev.dux.organizer.today

import dev.dux.organizer.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {
    @Test fun greetingFollowsTheHour() {
        assertEquals(R.string.greeting_morning, greetingFor(LocalTime.of(5, 0)))
        assertEquals(R.string.greeting_morning, greetingFor(LocalTime.of(11, 59)))
        assertEquals(R.string.greeting_afternoon, greetingFor(LocalTime.of(12, 0)))
        assertEquals(R.string.greeting_evening, greetingFor(LocalTime.of(19, 0)))
        assertEquals(R.string.greeting_evening, greetingFor(LocalTime.of(2, 0)))
    }
}
