package com.ferlagod.rocinante.utils

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPaceTest {

    private val today = LocalDate.of(2026, 8, 12)

    // ── Qué parte se lleva leída ──

    @Test
    fun `por pagina hace falta saber cuantas tiene`() {
        assertEquals(0.5, ReadingPace.fractionRead(150, isPercent = false, totalPages = 300)!!, 0.001)
        assertNull(ReadingPace.fractionRead(150, isPercent = false, totalPages = null))
        assertNull(ReadingPace.fractionRead(150, isPercent = false, totalPages = 0))
    }

    @Test
    fun `por porcentaje no hace falta`() {
        assertEquals(0.25, ReadingPace.fractionRead(25, isPercent = true, totalPages = null)!!, 0.001)
    }

    @Test
    fun `sin empezar o acabado no hay fraccion`() {
        assertNull(ReadingPace.fractionRead(0, isPercent = true, totalPages = 300))
        assertNull(ReadingPace.fractionRead(null, isPercent = true, totalPages = 300))
        assertNull(ReadingPace.fractionRead(100, isPercent = true, totalPages = 300))
        // Más páginas leídas que las que tiene: no es una fracción de nada.
        assertNull(ReadingPace.fractionRead(400, isPercent = false, totalPages = 300))
    }

    // ── Los días que faltan ──

    /** Empezado hace diez días y por la mitad: otros diez. */
    @Test
    fun `la mitad en diez dias son otros diez`() {
        val started = today.minusDays(9).toString() // el día de inicio cuenta, así que son 10
        assertEquals(10, ReadingPace.daysLeft(started, 0.5, today))
    }

    @Test
    fun `un cuarto en cinco dias son quince mas`() {
        val started = today.minusDays(4).toString()
        assertEquals(15, ReadingPace.daysLeft(started, 0.25, today))
    }

    /**
     * Hacia arriba y nunca a cero: quedando media jornada, la respuesta honrada es «un día».
     */
    @Test
    fun `se redondea hacia arriba, nunca a cero`() {
        val started = today.minusDays(8).toString() // 9 días
        // Al 90 % faltaría un día justo; al 95 %, medio, que sigue siendo un día.
        assertEquals(1, ReadingPace.daysLeft(started, 0.9, today))
        assertEquals(1, ReadingPace.daysLeft(started, 0.95, today))
    }

    /** Un libro empezado hoy no puede dividir por cero días. */
    @Test
    fun `empezado hoy cuenta como un dia`() {
        assertEquals(3, ReadingPace.daysLeft(today.toString(), 0.25, today))
    }

    @Test
    fun `sin fecha de inicio no se adivina nada`() {
        assertNull(ReadingPace.daysLeft(null, 0.5, today))
        assertNull(ReadingPace.daysLeft("", 0.5, today))
        assertNull(ReadingPace.daysLeft("sin fecha", 0.5, today))
    }

    @Test
    fun `sin progreso o acabado no hay nada que prever`() {
        val started = today.minusDays(5).toString()
        assertNull(ReadingPace.daysLeft(started, null, today))
        assertNull(ReadingPace.daysLeft(started, 0.0, today))
        assertNull(ReadingPace.daysLeft(started, 1.0, today))
    }

    /** La instancia manda la fecha con hora detrás a veces; se lee igual. */
    @Test
    fun `una fecha con hora detras se entiende`() {
        assertEquals(10, ReadingPace.daysLeft("2026-08-03T10:15:00Z", 0.5, today))
    }

    /**
     * A ritmo de tortuga la cuenta da cifras que no dicen nada —«faltan 40 años»—, y entonces es
     * mejor no enseñar nada que enseñar eso.
     */
    @Test
    fun `una previsión absurda no se enseña`() {
        val started = today.minusDays(364).toString()
        assertNull(ReadingPace.daysLeft(started, 0.01, today))
    }
}
