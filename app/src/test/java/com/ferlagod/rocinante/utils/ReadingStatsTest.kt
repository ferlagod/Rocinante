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
    fun `promedia las valoraciones y reparte por estrellas`() {
        val books = (1..4).map { book("b$it") }
        val enrichment = mapOf(
            "b1" to BookEnrichment(bookId = "b1", rating = 5.0),
            "b2" to BookEnrichment(bookId = "b2", rating = 4.5),
            "b3" to BookEnrichment(bookId = "b3", rating = 3.0),
            "b4" to BookEnrichment(bookId = "b4", rating = null)
        )

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(3, stats.ratedBooks)
        assertEquals(1, stats.booksWithoutRating)
        assertEquals(4.166, stats.averageRating!!, 0.001)
        // Las cinco enteras siempre, más la media estrella usada (4,5); nada de 0,5 ni 1,5…
        assertEquals(
            listOf(5.0, 4.5, 4.0, 3.0, 2.0, 1.0),
            stats.ratingDistribution.map { it.rating }
        )
        assertEquals(
            listOf(1, 1, 0, 1, 0, 0),
            stats.ratingDistribution.map { it.count }
        )
    }

    @Test
    fun `sin valoraciones no hay media`() {
        val stats = ReadingStatsCalculator.compute(
            listOf(book("a")),
            mapOf("a" to BookEnrichment(bookId = "a")),
            currentYear = 2026
        )

        assertEquals(null, stats.averageRating)
        assertFalse(stats.hasRatingData)
    }

    @Test
    fun `mide los días de lectura solo con las dos fechas`() {
        val books = listOf(book("a"), book("b"), book("c"))
        val enrichment = mapOf(
            // 10 días, terminado este año.
            "a" to BookEnrichment(bookId = "a", started = "2026-01-01", finished = "2026-01-11"),
            // 20 días, de un año anterior.
            "b" to BookEnrichment(bookId = "b", started = "2025-03-01", finished = "2025-03-21"),
            // Sin fecha de inicio: no se puede medir, que es el caso más común en BookWyrm.
            "c" to BookEnrichment(bookId = "c", finished = "2026-05-05")
        )

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(2, stats.booksWithReadingDays)
        assertEquals(10.0, stats.avgReadingDaysThisYear!!, 0.001)
        assertEquals(15.0, stats.avgReadingDaysAllTime!!, 0.001)
    }

    @Test
    fun `descarta tramos con las fechas invertidas`() {
        val books = listOf(book("a"))
        val enrichment = mapOf(
            "a" to BookEnrichment(bookId = "a", started = "2026-05-10", finished = "2026-05-01")
        )

        val stats = ReadingStatsCalculator.compute(books, enrichment, currentYear = 2026)

        assertEquals(0, stats.booksWithReadingDays)
        assertEquals(null, stats.avgReadingDaysAllTime)
        assertFalse(stats.hasReadingDays)
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
