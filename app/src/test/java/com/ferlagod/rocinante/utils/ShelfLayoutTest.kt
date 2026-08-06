package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLayoutTest {

    @Test
    fun `sin nada guardado se usa el orden por defecto`() {
        assertEquals(ShelfLayout.DEFAULT, ShelfLayout.decode(null, null))
        assertEquals(ShelfLayout.DEFAULT, ShelfLayout.decode("", ""))
        assertEquals(ShelfLayout.DEFAULT, ShelfLayout.decode("   ", null))
    }

    @Test
    fun `lo guardado se vuelve a leer igual`() {
        val layout = ShelfLayout.DEFAULT
            .toggled(ShelfSection.TO_READ)
            .withAlignment(ShelfAlignment.BOTTOM)
            .copy(sections = ShelfLayout.DEFAULT.sections.reversed())

        assertEquals(layout, ShelfLayout.decode(layout.encode(), layout.alignment.id))
    }

    @Test
    fun `leyendo y leidos no se pueden apagar`() {
        val layout = ShelfLayout.DEFAULT
            .toggled(ShelfSection.READING)
            .toggled(ShelfSection.READ)

        assertEquals(ShelfLayout.DEFAULT, layout)
        assertTrue(layout.isVisible(ShelfSection.READING))
        assertTrue(layout.isVisible(ShelfSection.READ))
    }

    @Test
    fun `un guardado manipulado no deja la pantalla sin estanterias`() {
        // Nadie llega a esto desde el diálogo; solo editando el fichero de ajustes a mano.
        val layout = ShelfLayout.decode("-stopped_reading,-to_read,-reading,-read", null)

        assertFalse(layout.isVisible(ShelfSection.STOPPED_READING))
        assertFalse(layout.isVisible(ShelfSection.TO_READ))
        assertEquals(
            listOf(ShelfSection.READING, ShelfSection.READ),
            layout.visibleSections
        )
    }

    @Test
    fun `las apagadas conservan su sitio para cuando se reactiven`() {
        val hidden = ShelfLayout.DEFAULT.toggled(ShelfSection.STOPPED_READING)
        assertFalse(hidden.isVisible(ShelfSection.STOPPED_READING))

        val restored = ShelfLayout.decode(hidden.encode(), null)
            .toggled(ShelfSection.STOPPED_READING)
        assertTrue(restored.isVisible(ShelfSection.STOPPED_READING))
        assertEquals(
            ShelfLayout.DEFAULT.sections.indexOf(ShelfSection.STOPPED_READING),
            restored.sections.indexOf(ShelfSection.STOPPED_READING)
        )
    }

    @Test
    fun `una estanteria nueva aparece aunque no estuviera guardada`() {
        // Disposición vieja, de cuando la pantalla solo tenía dos estanterías.
        val layout = ShelfLayout.decode("reading,read", null)

        assertEquals(ShelfSection.READING, layout.sections[0])
        assertEquals(ShelfSection.READ, layout.sections[1])
        assertEquals(ShelfSection.entries.size, layout.sections.size)
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun `una estanteria retirada en el futuro se ignora sin romper el resto`() {
        val layout = ShelfLayout.decode("read,estanteria_que_ya_no_existe,-to_read", null)

        assertEquals(ShelfSection.READ, layout.sections[0])
        assertEquals(ShelfSection.TO_READ, layout.sections[1])
        assertTrue(ShelfSection.TO_READ in layout.hidden)
        assertEquals(ShelfSection.entries.size, layout.sections.size)
    }

    @Test
    fun `una posicion desconocida se queda arriba`() {
        assertEquals(ShelfAlignment.TOP, ShelfLayout.decode(null, "de-lado").alignment)
        assertEquals(ShelfAlignment.BOTTOM, ShelfLayout.decode(null, "bottom").alignment)
    }

    @Test
    fun `los ids guardados no deben cambiar entre versiones`() {
        // Si esta prueba falla es que se ha renombrado un id: las disposiciones ya guardadas en
        // los dispositivos dejarían de reconocerse y la estantería se movería al final.
        assertEquals(
            listOf("stopped_reading", "to_read", "reading", "read"),
            ShelfSection.entries.map { it.id }
        )
        // Y estos son los que entiende BookWyrm en sus URL.
        assertEquals(
            listOf("stopped-reading", "to-read", "reading", "read"),
            ShelfSection.entries.map { it.slug }
        )
    }
}
