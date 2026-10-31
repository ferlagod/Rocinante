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

    @Test
    fun `extractReadthroughId obtiene el id del formulario reading-progress`() {
        val html = """
            <html><body>
            <form name="reading-progress-123" action="/reading-status/update/42/" method="POST">
                <input type="hidden" name="id" value="55">
                <input type="hidden" name="progress" value="120">
            </form>
            </body></html>
        """.trimIndent()

        val id = BookWyrmScraper.extractReadthroughId(html)
        assertEquals("55", id)
    }

    @Test
    fun `la fecha de inicio del readthrough activo se preserva y no se confunde con add-modal`() {
        val activeModal = readthroughModals("55", "2026-02-14", "")
        val html = """
            <html><body>
            $addModal
            $activeModal
            <form name="reading-progress-123" action="/reading-status/update/42/" method="POST">
                <input type="hidden" name="id" value="55">
            </form>
            </body></html>
        """.trimIndent()

        val activeId = BookWyrmScraper.extractReadthroughId(html)
        assertEquals("55", activeId)
        val parsed = BookWyrmScraper.parseReadDates(html)
        val activeStartDate = parsed.readthroughs.firstOrNull { it.id == activeId }?.startDate
        assertEquals("2026-02-14", activeStartDate)
    }

    @Test
    fun `parseReadDates soporta acciones de formulario con barra final como genera Django`() {
        val html = """
            <html><body>
            <div class="modal" id="edit_readthrough_12">
              <form name="add-readthrough-12" action="/edit-readthrough/" method="POST">
                <input type="hidden" name="id" value="12">
                <input type="date" name="start_date" value="2026-04-01">
                <input type="date" name="finish_date" value="2026-04-10">
              </form>
            </div>
            <div class="modal" id="add-readthrough">
              <form name="add-readthrough-" action="/create-readthrough/" method="POST">
                <input type="hidden" name="book" value="99">
                <input type="hidden" name="user" value="3">
              </form>
            </div>
            </body></html>
        """.trimIndent()

        val parsed = BookWyrmScraper.parseReadDates(html)
        assertEquals("99", parsed.bookId)
        assertEquals("3", parsed.userId)
        assertEquals(1, parsed.readthroughs.size)
        assertEquals("12", parsed.readthroughs[0].id)
        assertEquals("2026-04-01", parsed.readthroughs[0].startDate)
        assertEquals("2026-04-10", parsed.readthroughs[0].finishDate)
    }

    @Test
    fun `extractEditionId extrae el ID de edicion desde atributos data y formulario reading-status`() {
        val htmlData = """<button data-shelve-button-book="77">Start reading</button>"""
        assertEquals("77", BookWyrmScraper.extractEditionId(htmlData))

        val htmlAction = """<form action="/reading-status/reading/88/" method="POST"></form>"""
        assertEquals("88", BookWyrmScraper.extractEditionId(htmlAction))
    }

    @Test
    fun `extractReadthroughId toma la lectura activa del modal de terminar de leer`() {
        val html = """
            <html><body>
            <form name="finish-reading-124985abc" action="/reading-status/finish/124985" method="post">
                <input type="hidden" name="id" value="101">
                <input type="date" name="start_date" value="2026-05-01">
                <input type="date" name="finish_date" value="2026-10-31">
            </form>
            </body></html>
        """.trimIndent()

        assertEquals("101", BookWyrmScraper.extractReadthroughId(html))
        assertEquals("2026-05-01", BookWyrmScraper.extractActiveReadthrough(html)?.startDate)
    }

    @Test
    fun `extractReadthroughId no confunde una lectura terminada con la activa`() {
        // Relectura: solo hay una lectura anterior ya cerrada y el modal de terminar viene
        // sin id. Devolver la antigua copiaba su fecha de inicio a la lectura nueva.
        val html = """
            <html><body>
            <form action="/edit-readthrough/" method="POST">
                <input type="hidden" name="id" value="55">
                <input type="date" name="start_date" value="2024-01-01">
                <input type="date" name="finish_date" value="2024-02-01">
            </form>
            <form name="finish-reading-124985abc" action="/reading-status/finish/124985" method="post">
                <input type="hidden" name="id" value="">
                <input type="date" name="start_date" value="">
            </form>
            </body></html>
        """.trimIndent()

        assertNull(BookWyrmScraper.extractReadthroughId(html))
        assertNull(BookWyrmScraper.extractActiveReadthrough(html))
    }
}
