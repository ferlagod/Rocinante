package com.ferlagod.rocinante.utils

import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ReadthroughDates
import com.ferlagod.rocinante.data.model.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShelvedDatesTest {

    private val today = LocalDate.of(2026, 11, 14)

    private fun parse(text: String, lang: String?) = ShelvedDateParser.parse(text, lang, today)

    @Test
    fun `entiende el formato largo de cada idioma`() {
        val expected = LocalDate.of(2023, 11, 5)
        assertEquals(expected, parse("Nov. 5, 2023", "en"))
        assertEquals(expected, parse("5 de noviembre de 2023", "es"))
        assertEquals(expected, parse("5. marraskuuta 2023", "fi"))
        assertEquals(expected, parse("5 november 2023", "sv"))
        assertEquals(expected, parse("5. November 2023", "de"))
        assertEquals(expected, parse("5 novembre 2023", "fr"))
        assertEquals(expected, parse("5 de novembre de 2023", "ca"))
        assertEquals(expected, parse("5 listopada 2023", "pl"))
        assertEquals(expected, parse("2023 m. lapkričio 5 d.", "lt"))
        assertEquals(expected, parse("2023年11月5日", "zh-hans"))
        assertEquals(expected, parse("2023년 11월 5일", "ko"))
        assertEquals(expected, parse("5.11.2023", null))
    }

    @Test
    fun `entiende las abreviaturas de estilo AP y la elisión del catalán`() {
        assertEquals(LocalDate.of(2024, 9, 1), parse("Sept. 1, 2024", "en"))
        assertEquals(LocalDate.of(2024, 3, 12), parse("March 12, 2024", "en"))
        assertEquals(LocalDate.of(2024, 10, 3), parse("3 d’octubre de 2024", "ca"))
    }

    @Test
    fun `sin idioma busca el mes en todos`() {
        assertEquals(LocalDate.of(2022, 6, 30), parse("30 juni 2022", null))
        assertEquals(LocalDate.of(2022, 1, 2), parse("2. tammikuuta 2022", "pt-br"))
    }

    @Test
    fun `hoy y ayer en varios idiomas`() {
        assertEquals(today, parse("today", "en"))
        assertEquals(today, parse("i dag", "sv"))
        assertEquals(today, parse("aujourd’hui", "fr"))
        assertEquals(today.minusDays(1), parse("eilen", "fi"))
        assertEquals(today.minusDays(1), parse("ayer", "es"))
        assertEquals(today.minusDays(1), parse("i går", "nb"))
        assertEquals(today, parse("오늘", "ko"))
    }

    @Test
    fun `lo que no es una fecha no se inventa`() {
        assertNull(parse("", "en"))
        assertNull(parse("None", "en"))
        assertNull(parse("Nov. 31, 2023", "en"))
        assertNull(parse("5 de noviembre", "es"))
    }

    private val shelfHtml = """
        <html lang="fi"><body><table>
        <tr class="book-preview">
          <td class="book-preview-top-row"><a href="/book/10/s/kirja"><img src="/c.jpg"></a></td>
          <td data-title="Nimi"><a href="/book/10/s/kirja">Eurotrash</a></td>
          <td data-title="Tekijä"><a href="/author/1">X</a></td>
          <td data-title="Hyllytetty" class="nowrap-date-whitespace">5. marraskuuta 2023</td>
          <td data-title="Aloitettu" class="nowrap-date-whitespace"></td>
          <td data-title="Lopetettu" class="nowrap-date-whitespace"></td>
        </tr>
        <tr class="book-preview">
          <td class="book-preview-top-row"><a href="/book/11/s/toinen"><img src="/c.jpg"></a></td>
          <td data-title="Nimi"><a href="/book/11/s/toinen">Toinen</a></td>
          <td data-title="Tekijä"></td>
          <td data-title="Hyllytetty" class="nowrap-date-whitespace">1. tammikuuta 2024</td>
          <td data-title="Aloitettu" class="nowrap-date-whitespace"></td>
          <td data-title="Lopetettu" class="nowrap-date-whitespace">31. joulukuuta 2023</td>
        </tr>
        </table>
        <nav class="pagination"><a class="pagination-previous" href="?sort=-finish_date&amp;page=17">Edellinen</a></nav>
        </body></html>
    """.trimIndent()

    @Test
    fun `trocea la página de la estantería con sus fechas y la página anterior`() {
        val page = BookWyrmScraper.parseShelvedDatesPage(shelfHtml, "https://example.org/", today)

        assertTrue(page.hasDateColumns)
        assertEquals(17, page.previousPage)
        assertEquals(2, page.rows.size)
        val first = page.rows[0]
        assertEquals("https://example.org/book/10", first.bookUrl)
        assertEquals("Eurotrash", first.title)
        assertEquals("2023-11-05", first.shelved)
        assertFalse(first.hasFinish)
        assertTrue(page.rows[1].hasFinish)
    }

    @Test
    fun `sin columnas de fechas lo dice`() {
        val html = """<html><body><table><tr class="book-preview">
            <td><a href="/book/10/s/x">X</a></td><td data-title="Rating"></td>
            </tr></table></body></html>"""
        val page = BookWyrmScraper.parseShelvedDatesPage(html, "https://example.org/", today)
        assertFalse(page.hasDateColumns)
        assertNull(page.previousPage)
    }

    private fun book(id: String, title: String = id) =
        ShelfBookItem(id = id, title = title, cover = null)

    @Test
    fun `la fecha de archivado solo cubre a quien no tiene ninguna fecha de fin`() {
        val books = listOf(
            book("https://e.org/book/1"),
            book("https://e.org/book/2"),
            book("https://e.org/book/3"),
            book("https://otra.org/book/99", title = "Federado")
        )
        val enrichment = mapOf(
            "https://e.org/book/1" to BookEnrichment(bookId = "1", finished = "2020-01-01"),
            "https://e.org/book/2" to BookEnrichment(
                bookId = "2",
                started = "2019-01-01",
                readthroughs = listOf(ReadthroughDates("7", "2019-01-01", null))
            ),
            "https://e.org/book/3" to BookEnrichment(
                bookId = "3",
                readthroughs = listOf(ReadthroughDates("8", null, "2018-05-05"))
            ),
            "https://otra.org/book/99" to BookEnrichment(bookId = "99")
        )
        val shelved = ShelvedDates(
            byUrl = mapOf(
                "https://e.org/book/1" to "2021-01-01",
                "https://e.org/book/2" to "2022-02-02",
                "https://e.org/book/3" to "2023-03-03"
            ),
            byTitle = mapOf(ShelvedDates.titleKey("Federado") to "2024-04-04")
        )

        val result = applyShelvedFallback(books, enrichment, shelved)

        assertEquals(setOf("https://e.org/book/2", "https://otra.org/book/99"), result.approximated)
        assertEquals("2020-01-01", result.enrichment["https://e.org/book/1"]?.finished)
        val second = result.enrichment["https://e.org/book/2"]
        assertEquals("2022-02-02", second?.finished)
        // Sin la de inicio: restarla de la de archivado daría una duración inventada.
        assertNull(second?.started)
        assertEquals("2024-04-04", result.enrichment["https://otra.org/book/99"]?.finished)
    }

    @Test
    fun `las estadísticas cuentan las fechas aproximadas y no los libros pendientes`() {
        val books = listOf(book("a"), book("b"), book("c"), book("d"))
        val enrichment = mapOf(
            "a" to BookEnrichment(bookId = "a", finished = "2025-03-03"),
            "b" to BookEnrichment(bookId = "b"),
            "c" to BookEnrichment(bookId = "c")
            // "d" aún no se ha leído de su página.
        )
        val shelved = ShelvedDates(byUrl = mapOf("b" to "2024-06-01"))

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026, shelvedDates = shelved)

        assertEquals(1, stats.booksWithShelvedDate)
        // Solo «c»: «b» lleva la de archivado y de «d» todavía no se sabe nada.
        assertEquals(1, stats.booksWithoutFinishDate)
        assertEquals(
            listOf(ReadingStats.YearCount(2024, 1), ReadingStats.YearCount(2025, 1)),
            stats.booksPerYear
        )
        assertEquals(0, stats.booksWithReadingDays)

        val only2024 = ReadingStatsCalculator.compute(
            books, enrichment, currentYear = 2026, filterYear = 2024, shelvedDates = shelved
        )
        assertEquals(1, only2024.booksWithShelvedDate)
        val only2025 = ReadingStatsCalculator.compute(
            books, enrichment, currentYear = 2026, filterYear = 2025, shelvedDates = shelved
        )
        assertEquals(0, only2025.booksWithShelvedDate)
    }
}
