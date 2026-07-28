package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReadingGoalPaceTest {

    @Test
    fun `a mitad de año la mitad de la meta va al día`() {
        // 2026 no es bisiesto: el día 182 de 365 es algo más de la mitad.
        val pace = ReadingGoalPace.booksAheadOfSchedule(
            booksRead = 10,
            goal = 20,
            today = LocalDate.of(2026, 7, 1)
        )
        assertEquals(0, pace)
    }

    @Test
    fun `cuenta el adelanto y el retraso en libros`() {
        val date = LocalDate.of(2026, 7, 1)
        assertEquals(3, ReadingGoalPace.booksAheadOfSchedule(13, 20, date))
        assertEquals(-4, ReadingGoalPace.booksAheadOfSchedule(6, 20, date))
    }

    @Test
    fun `el primer día del año la meta aún no exige nada`() {
        val pace = ReadingGoalPace.booksAheadOfSchedule(0, 50, LocalDate.of(2026, 1, 1))
        assertEquals(0, pace)
    }

    @Test
    fun `el último día del año exige la meta entera`() {
        val date = LocalDate.of(2026, 12, 31)
        assertEquals(0, ReadingGoalPace.booksAheadOfSchedule(20, 20, date))
        assertEquals(-8, ReadingGoalPace.booksAheadOfSchedule(12, 20, date))
    }

    @Test
    fun `sin meta no hay ritmo que calcular`() {
        assertNull(ReadingGoalPace.booksAheadOfSchedule(5, 0, LocalDate.of(2026, 7, 1)))
        assertNull(ReadingGoalPace.booksAheadOfSchedule(5, -3, LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `el año bisiesto reparte la meta entre 366 días`() {
        // Día 183 de 366 es justo la mitad de 2024.
        val pace = ReadingGoalPace.booksAheadOfSchedule(5, 10, LocalDate.of(2024, 7, 1))
        assertEquals(0, pace)
    }
}
