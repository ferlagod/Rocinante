package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El formato en que se recuerda la ordenación de cada estantería.
 *
 * Se prueba aquí y no a ojo porque ya falló una vez de la peor manera: lo que se guardaba era
 * el texto de la plantilla en vez de sus valores, así que se escribía sin error, se leía sin
 * error, y la estantería salía en el orden del servidor como si nunca se hubiera elegido nada.
 * Una prueba de ida y vuelta lo habría cazado al momento.
 *
 * Es una copia del formato que usa `MyBooksScreen`, que no puede importarse por ser privado de
 * su fichero. Si allí cambia, esto debe cambiar con ello.
 */
class ShelfSortStorageTest {

    private fun encode(modes: Map<String, String>): String =
        modes.entries.joinToString(",") { "${it.key}=${it.value}" }

    private fun decode(raw: String?): Map<String, String> =
        raw.orEmpty().split(",").mapNotNull { entry ->
            val parts = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            parts[0].trim().takeIf { it.isNotEmpty() }?.let { it to parts[1].trim() }
        }.toMap()

    @Test
    fun `lo escrito lleva los valores, no la plantilla`() {
        val encoded = encode(mapOf("read" to "FINISHED_DESC"))
        assertEquals("read=FINISHED_DESC", encoded)
        // El fallo real: salía "${it.key}=${it.value.name}" y se guardaba tal cual.
        assertTrue("no debe quedar nada sin sustituir", !encoded.contains("$"))
        assertTrue(!encoded.contains("it.key"))
    }

    @Test
    fun `ida y vuelta`() {
        val modes = mapOf("read" to "FINISHED_DESC", "to-read" to "TITLE_ASC")
        assertEquals(modes, decode(encode(modes)))
    }

    /** Cada estantería la suya: es justo lo que pidió el usuario. */
    @Test
    fun `dos estanterias con ordenaciones distintas`() {
        val stored = encode(mapOf("read" to "FINISHED_DESC", "to-read" to "TITLE_ASC"))
        assertEquals("FINISHED_DESC", decode(stored)["read"])
        assertEquals("TITLE_ASC", decode(stored)["to-read"])
    }

    @Test
    fun `poner al dia una estanteria no toca las demas`() {
        val before = decode(encode(mapOf("read" to "FINISHED_DESC", "to-read" to "TITLE_ASC")))
        val after = decode(encode(before + ("read" to "TITLE_DESC")))
        assertEquals("TITLE_DESC", after["read"])
        assertEquals("TITLE_ASC", after["to-read"])
    }

    @Test
    fun `un texto estropeado no revienta la lectura`() {
        assertTrue(decode(null).isEmpty())
        assertTrue(decode("").isEmpty())
        assertTrue(decode("basura,sin=igual=doble,=vacio").keys.none { it.isEmpty() })
    }

    /** Las estanterías propias llevan guiones y números en su identificador. */
    @Test
    fun `una estanteria propia tambien se recuerda`() {
        val stored = encode(mapOf("favoritter-315769" to "TITLE_ASC"))
        assertEquals("TITLE_ASC", decode(stored)["favoritter-315769"])
    }
}
