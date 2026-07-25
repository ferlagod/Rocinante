package com.ferlagod.rocinante.utils

import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsTest {

    private fun book(id: String, pages: Int? = null) =
        ShelfBookItem(id = id, title = id, cover = null, pages = pages)

    private fun finished(id: String, date: String?) =
        id to BookEnrichment(bookId = id, finished = date)

    @Test
    fun `cuenta libros, páginas y lecturas del año en curso`() {
        val books = listOf(book("a", 100), book("b", 250), book("c", null))
        val enrichment = mapOf(
            finished("a", "2024-05-01"),
            finished("b", "2026-01-30"),
            finished("c", "2026-07-01")
        )

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(3, stats.totalBooks)
        assertEquals(2, stats.booksThisYear)
        assertEquals(350, stats.totalPages)
        assertEquals(0, stats.booksWithoutFinishDate)
        // El libro sin páginas no suma, pero se cuenta para poder advertirlo.
        assertEquals(1, stats.booksWithoutPages)
    }

    @Test
    fun `rellena los años sin lecturas para que el eje no mienta`() {
        val books = listOf(book("a"), book("b"))
        val enrichment = mapOf(finished("a", "2020-01-01"), finished("b", "2023-01-01"))

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(
            listOf(
                ReadingStats.YearCount(2020, 1),
                ReadingStats.YearCount(2021, 0),
                ReadingStats.YearCount(2022, 0),
                ReadingStats.YearCount(2023, 1)
            ),
            stats.booksPerYear
        )
    }

    @Test
    fun `los libros sin fecha se cuentan aparte y no entran en el gráfico`() {
        val books = listOf(book("a"), book("b"), book("c"))
        val enrichment = mapOf(finished("a", "2025-03-03"), finished("b", null))

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(3, stats.totalBooks)
        assertEquals(2, stats.booksWithoutFinishDate)
        assertEquals(listOf(ReadingStats.YearCount(2025, 1)), stats.booksPerYear)
    }

    @Test
    fun `descarta fechas imposibles o futuras`() {
        val books = listOf(book("a"), book("b"), book("c"))
        val enrichment = mapOf(
            finished("a", "0001-01-01"),
            finished("b", "2099-01-01"),
            finished("c", "no es una fecha")
        )

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertTrue(stats.booksPerYear.isEmpty())
        assertEquals(3, stats.booksWithoutFinishDate)
    }

    @Test
    fun `separa coautores pero no un apellido escrito primero`() {
        // "Linus Torvalds, David Diamond" son dos personas; "Henry, Ford" es una sola.
        assertEquals(
            listOf("Linus Torvalds", "David Diamond"),
            ReadingStatsCalculator.splitAuthors("Linus Torvalds, David Diamond")
        )
        assertEquals(listOf("Henry, Ford"), ReadingStatsCalculator.splitAuthors("Henry, Ford"))
        assertEquals(listOf("Ken Follett"), ReadingStatsCalculator.splitAuthors("Ken Follett"))
    }

    @Test
    fun `ordena los autores por libros y deja como mucho diez`() {
        val books = (1..16).map { book("b$it") }
        // Un autor con tres libros, otro con dos, y once más con uno cada uno:
        // trece autores distintos, más de los que caben en el gráfico.
        val enrichment = buildMap {
            put("b1", BookEnrichment(bookId = "b1", authorName = "Jussi Adler-Olsen"))
            put("b2", BookEnrichment(bookId = "b2", authorName = "Jussi Adler-Olsen"))
            put("b3", BookEnrichment(bookId = "b3", authorName = "Jussi Adler-Olsen"))
            put("b4", BookEnrichment(bookId = "b4", authorName = "Sara Blædel"))
            put("b5", BookEnrichment(bookId = "b5", authorName = "Sara Blædel"))
            (6..16).forEach { put("b$it", BookEnrichment(bookId = "b$it", authorName = "Autor $it")) }
        }

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(10, stats.topAuthors.size)
        assertEquals(ReadingStats.AuthorCount("Jussi Adler-Olsen", 3), stats.topAuthors[0])
        assertEquals(ReadingStats.AuthorCount("Sara Blædel", 2), stats.topAuthors[1])
        assertTrue(stats.hasAuthorData)
    }

    @Test
    fun `cuenta los libros sin autor por separado`() {
        val books = listOf(book("a"), book("b"))
        val enrichment = mapOf("a" to BookEnrichment(bookId = "a", authorName = "Kate Quinn"))

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(1, stats.booksWithoutAuthor)
        assertEquals(listOf(ReadingStats.AuthorCount("Kate Quinn", 1)), stats.topAuthors)
        assertFalse(stats.hasAuthorData)
    }

    @Test
    fun `un solo año no da serie temporal`() {
        val books = listOf(book("a"), book("b"))
        val enrichment = mapOf(finished("a", "2026-01-01"), finished("b", "2026-02-01"))

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(listOf(ReadingStats.YearCount(2026, 2)), stats.booksPerYear)
        assertFalse(stats.hasChartData)
    }
}
