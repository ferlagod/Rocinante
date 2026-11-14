package com.ferlagod.rocinante.data.api

import com.ferlagod.rocinante.utils.BookWyrmUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Más respuestas y páginas de BookWyrm que la app tiene que entender. */
class ServerResponsesTest {

    @Test
    fun `solo la redirección a la estantería nueva dice cómo se llama`() {
        assertEquals("favoritos-12", BookWyrmScraper.shelfIdentifierFromLocation("/user/ana/books/favoritos-12"))
        assertEquals("leidos-2024", BookWyrmScraper.shelfIdentifierFromLocation("https://e.org/user/ana/shelf/leidos-2024/"))
        // Formulario rechazado: BookWyrm redirige a la lista de estanterías.
        assertNull(BookWyrmScraper.shelfIdentifierFromLocation("/user/ana/books"))
        assertNull(BookWyrmScraper.shelfIdentifierFromLocation("/user/ana/books/"))
        assertNull(BookWyrmScraper.shelfIdentifierFromLocation(null))
    }

    @Test
    fun `el origen lleva el puerto solo si no es el de siempre`() {
        assertEquals("https://e.org", NetworkClient.originOf("https://E.org/".toHttpUrl()))
        assertEquals("https://e.org:8443", NetworkClient.originOf("https://e.org:8443/".toHttpUrl()))
        assertEquals("http://localhost:1333", NetworkClient.originOf("http://localhost:1333/".toHttpUrl()))
    }

    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun `las fechas de los estados se entienden en otros idiomas y sin año`() {
        assertEquals(LocalDate.of(2024, 8, 22), BookWyrmUtils.parseReviewDate("Aug. 22, 2024", "en", today))
        assertEquals(LocalDate.of(2026, 8, 22), BookWyrmUtils.parseReviewDate("Aug. 22", "en", today))
        assertEquals(LocalDate.of(2026, 8, 22), BookWyrmUtils.parseReviewDate("22 de agosto", "es", today))
        assertEquals(LocalDate.of(2025, 3, 5), BookWyrmUtils.parseReviewDate("5. maaliskuuta 2025", "fi", today))
        assertEquals(LocalDate.of(2026, 3, 5), BookWyrmUtils.parseReviewDate("5 mars", null, today))
        assertEquals(LocalDate.of(2024, 8, 22), BookWyrmUtils.parseReviewDate("2024-08-22T10:00:00Z", null, today))
        // Lo de hoy viene como frase relativa.
        assertEquals(today, BookWyrmUtils.parseReviewDate("hace 3 horas", "es", today))
        assertEquals(today, BookWyrmUtils.parseReviewDate("now", "en", today))
        assertNull(BookWyrmUtils.parseReviewDate("", "en", today))
    }

    @Test
    fun `lee lo publicado por el usuario en las pestañas del libro`() {
        val html = """
            <html lang="es"><body><div class="block" id="reviews"><div class="columns">
              <div class="block" itemprop="review">
                <article class="card"><header class="card-header"><div class="media">
                  <a href="/user/ana" itemprop="author"><span itemprop="name">Ana</span></a>
                  <div class="breadcrumb"><ul><li><a href="https://e.org/user/ana/review/7#anchor-7">22 de agosto</a></li></ul></div>
                </div></header>
                <section class="card-content"><div itemprop="reviewBody">Muy bueno</div></section></article>
              </div>
            </div></div></body></html>
        """.trimIndent()
        val items = BookWyrmScraper.parseOwnStatusesPage(org.jsoup.Jsoup.parse(html), "https://e.org/")
        assertEquals(1, items.size)
        assertEquals("Review", items[0].type)
        assertEquals("Ana", items[0].actorName)
    }
}
