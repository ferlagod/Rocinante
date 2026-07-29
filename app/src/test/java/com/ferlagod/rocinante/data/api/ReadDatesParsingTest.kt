/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil, pero
 * SIN GARANTÍA ALGUNA; ni siquiera la garantía implícita
 * MERCANTIL o de APTITUD PARA UN PROPÓSITO DETERMINADO.
 * Consulte los detalles de la Licencia Pública General GNU para obtener
 * una información más detallada.
 *
 * Debería haber recibido una copia de la Licencia Pública General GNU
 * junto a este programa.
 * En caso contrario, consulte <https://www.gnu.org/licenses/>.
 */
package com.ferlagod.rocinante.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de [BookWyrmScraper.parseReadDates] sobre un recorte de la página del libro con la
 * misma forma que la de BookWyrm (modal de edición por lectura, modal de borrado y modal de
 * alta). Corren en la JVM: `./gradlew :app:testDebugUnitTest`.
 */
class ReadDatesParsingTest {

    /**
     * Modal de una lectura ya registrada, tal y como lo dibuja BookWyrm: el id del modal lo
     * llevan también la cabecera y el modal de borrado, que no traen las fechas.
     */
    private fun readthroughModals(id: String, start: String, finish: String) = """
        <div class="modal" id="edit_readthrough_$id">
          <h2 id="edit_readthrough_${id}_header">Update read dates</h2>
          <form name="add-readthrough-$id" action="/edit-readthrough" method="POST">
            <input type="hidden" name="csrfmiddlewaretoken" value="tok">
            <input type="hidden" name="id" value="$id">
            <input type="hidden" name="book" value="42">
            <input type="hidden" name="user" value="7">
            <input type="date" name="start_date" class="input" value="$start">
            <input type="date" name="finish_date" class="input" value="$finish">
          </form>
        </div>
        <div class="modal" id="delete_readthrough_$id">
          <form name="delete-readthrough" action="/delete-readthrough" method="POST">
            <input type="hidden" name="id" value="$id">
          </form>
        </div>
    """.trimIndent()

    /** Modal de alta: siempre presente para quien ha iniciado sesión, con el id vacío. */
    private val addModal = """
        <div class="modal" id="add-readthrough">
          <form name="add-readthrough-" action="/create-readthrough" method="POST">
            <input type="hidden" name="csrfmiddlewaretoken" value="tok">
            <input type="hidden" name="id" value="">
            <input type="hidden" name="book" value="42">
            <input type="hidden" name="user" value="7">
            <input type="date" name="start_date" class="input" value="">
            <input type="date" name="finish_date" class="input" value="">
          </form>
        </div>
    """.trimIndent()

    @Test
    fun `un libro sin lecturas da los identificadores del formulario de alta`() {
        val parsed = BookWyrmScraper.parseReadDates("<html><body>$addModal</body></html>")

        assertEquals("42", parsed.bookId)
        assertEquals("7", parsed.userId)
        assertTrue(parsed.readthroughs.isEmpty())
    }

    @Test
    fun `cada lectura sale una sola vez y con sus fechas`() {
        val html = """
            <html><body>
            ${readthroughModals("5", "2026-01-02", "2026-01-20")}
            ${readthroughModals("9", "2026-03-01", "2026-03-15")}
            $addModal
            </body></html>
        """.trimIndent()

        val parsed = BookWyrmScraper.parseReadDates(html)

        assertEquals(listOf("5", "9"), parsed.readthroughs.map { it.id })
        assertEquals("2026-01-02", parsed.readthroughs[0].startDate)
        assertEquals("2026-01-20", parsed.readthroughs[0].finishDate)
        assertEquals("2026-03-15", parsed.readthroughs[1].finishDate)
    }

    @Test
    fun `una lectura a medias deja vacia la fecha que falta`() {
        val html = "<html><body>${readthroughModals("5", "2026-01-02", "")}$addModal</body></html>"

        val parsed = BookWyrmScraper.parseReadDates(html)

        assertEquals(1, parsed.readthroughs.size)
        assertEquals("2026-01-02", parsed.readthroughs[0].startDate)
        assertNull(parsed.readthroughs[0].finishDate)
    }

    @Test
    fun `una pagina sin formularios no inventa nada`() {
        val parsed = BookWyrmScraper.parseReadDates("<html><body><p>Sin sesión</p></body></html>")

        assertNull(parsed.bookId)
        assertNull(parsed.userId)
        assertTrue(parsed.readthroughs.isEmpty())
    }
}
