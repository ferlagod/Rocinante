package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Una sola lista para las cuatro estanterías de BookWyrm y las que el usuario se hace, porque
 * el orden es común. Lo que se prueba aquí no es que ordene —eso se ve— sino los casos que solo
 * salen con el tiempo: una estantería que aparece, una que desaparece, y lo guardado por una
 * versión anterior.
 */
class ShelfLayoutTest {

    private val builtIn = ShelfSection.entries.map { it.slug }

    @Test
    fun `sin nada guardado, el orden de siempre`() {
        val layout = ShelfLayout.decode(null, null)
        assertEquals(builtIn, layout.order)
        assertTrue(layout.hidden.isEmpty())
        assertEquals(ShelfAlignment.TOP, layout.alignment)
    }

    @Test
    fun `lo guardado manda`() {
        val layout = ShelfLayout.decode("read,to-read,reading,stopped-reading", null)
        assertEquals(listOf("read", "to-read", "reading", "stopped-reading"), layout.order)
    }

    /**
     * Las versiones anteriores guardaban los ids de la enumeración. Quien actualiza conserva su
     * orden en vez de volver al de fábrica.
     */
    @Test
    fun `lo guardado por una version anterior se entiende`() {
        val layout = ShelfLayout.decode("read,-to_read,reading,stopped_reading", null)
        assertEquals(listOf("read", "to-read", "reading", "stopped-reading"), layout.order)
        assertFalse(layout.isVisible("to-read"))
    }

    @Test
    fun `leyendo y leidos no se pueden apagar`() {
        val layout = ShelfLayout.DEFAULT
        assertFalse(layout.canHide("reading"))
        assertFalse(layout.canHide("read"))
        assertTrue(layout.canHide("to-read"))
        // Apagarlas no hace nada, en vez de fallar.
        assertEquals(layout, layout.toggled("reading"))
    }

    /** Un fichero manipulado a mano no debe dejar la pantalla sin ninguna estantería. */
    @Test
    fun `un guion delante de una que no se puede apagar se ignora`() {
        val layout = ShelfLayout.decode("-reading,-read,to-read", null)
        assertTrue(layout.isVisible("reading"))
        assertTrue(layout.isVisible("read"))
    }

    @Test
    fun `las propias del usuario se pueden apagar todas`() {
        val layout = ShelfLayout.decode("read,favoritter-315769", null)
        assertTrue(layout.canHide("favoritter-315769"))
        assertFalse(layout.toggled("favoritter-315769").isVisible("favoritter-315769"))
    }

    /** Esconder la que uno acaba de crear sería lo peor que podría hacer esta pantalla. */
    @Test
    fun `una estanteria nueva sale encendida y al final`() {
        val layout = ShelfLayout.decode("read,reading,to-read,stopped-reading", null)
        val visible = layout.visible(builtIn + "nueva-1")
        assertEquals("nueva-1", visible.last())
    }

    /**
     * Si el usuario borra una estantería en la web, o si la lista llega a medias por un fallo
     * de red, su sitio y su ajuste esperan a que vuelva.
     */
    @Test
    fun `una que desaparece conserva su sitio y su ajuste`() {
        val layout = ShelfLayout.decode("read,-favoritter-1,reading,to-read,stopped-reading", null)
        // No está entre las que hay: no se dibuja...
        assertFalse(layout.visible(builtIn).contains("favoritter-1"))
        // ...pero sigue guardada, apagada y en su sitio.
        assertTrue(layout.encode().contains("-favoritter-1"))
        assertFalse(layout.isVisible("favoritter-1"))
    }

    @Test
    fun `las cuatro de serie siempre estan, aunque falten en lo guardado`() {
        val layout = ShelfLayout.decode("favoritter-1", null)
        builtIn.forEach { assertTrue(it in layout.order) }
    }

    @Test
    fun `mover cambia el sitio`() {
        val layout = ShelfLayout.decode("a,b,c", null)
        assertEquals(listOf("b", "a", "c"), layout.moved(1, 0).order.take(3))
    }

    @Test
    fun `mover fuera de la lista no hace nada`() {
        val layout = ShelfLayout.decode("a,b", null)
        assertEquals(layout, layout.moved(0, 99))
        assertEquals(layout, layout.moved(-1, 0))
        assertEquals(layout, layout.moved(0, 0))
    }

    @Test
    fun `lo escrito se vuelve a leer igual`() {
        val layout = ShelfLayout.decode("read,-to-read,favoritter-315769,reading,stopped-reading", null)
        assertEquals(layout.order, ShelfLayout.decode(layout.encode(), null).order)
        assertEquals(layout.hidden, ShelfLayout.decode(layout.encode(), null).hidden)
    }

    @Test
    fun `un texto estropeado no deja la pantalla vacia`() {
        assertEquals(builtIn, ShelfLayout.decode(",,,", null).order)
        // Repetida: se queda con la primera, sin duplicar la tarjeta.
        assertEquals(1, ShelfLayout.decode("read,read", null).order.count { it == "read" })
    }

    @Test
    fun `la posicion se guarda aparte`() {
        assertEquals(ShelfAlignment.BOTTOM, ShelfLayout.decode(null, "bottom").alignment)
    }
}
