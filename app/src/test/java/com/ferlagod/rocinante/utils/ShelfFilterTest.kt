package com.ferlagod.rocinante.utils

import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ReadthroughDates
import com.ferlagod.rocinante.data.model.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El recorte tiene que quedarse con los mismos libros que contó la gráfica del perfil. Lo que
 * se prueba aquí son justo los casos en que «filtrar por lo que pone la barra» no es lo obvio:
 * relecturas, idiomas escritos de dos maneras y libros de los que aún no se sabe nada.
 */
class ShelfFilterTest {

    private fun book(
        id: String,
        languages: List<String>? = null,
        format: String? = null
    ) = ShelfBookItem(
        id = id,
        title = id,
        cover = null,
        languages = languages,
        physicalFormat = format
    )

    @Test
    fun `un ano recoge el libro terminado ese ano`() {
        val enrichment = mapOf("a" to BookEnrichment(bookId = "a", finished = "2025-03-04"))
        val result = ShelfFiltering.apply(listOf(book("a")), enrichment, ShelfFilter.Year(2025))
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `una relectura cuenta en cada ano en que termino`() {
        val enrichment = mapOf(
            "a" to BookEnrichment(
                bookId = "a",
                finished = "2020-01-01",
                readthroughs = listOf(
                    ReadthroughDates(id = "1", finished = "2020-01-01"),
                    ReadthroughDates(id = "2", finished = "2025-06-30")
                )
            )
        )
        val books = listOf(book("a"))
        assertTrue(ShelfFiltering.apply(books, enrichment, ShelfFilter.Year(2020)).isNotEmpty())
        assertTrue(ShelfFiltering.apply(books, enrichment, ShelfFilter.Year(2025)).isNotEmpty())
        assertTrue(ShelfFiltering.apply(books, enrichment, ShelfFilter.Year(2023)).isEmpty())
    }

    /**
     * Con relecturas manda la lista y no el campo suelto: si no, un libro releído aparecería
     * además en el año que dice `finished`, que es el de la última.
     */
    @Test
    fun `con relecturas no cuenta ademas la fecha suelta`() {
        val enrichment = mapOf(
            "a" to BookEnrichment(
                bookId = "a",
                finished = "2019-05-05",
                readthroughs = listOf(ReadthroughDates(id = "1", finished = "2025-06-30"))
            )
        )
        assertTrue(
            ShelfFiltering.apply(listOf(book("a")), enrichment, ShelfFilter.Year(2019)).isEmpty()
        )
    }

    @Test
    fun `un libro sin datos por libro no entra en un ano ni en una nota`() {
        val books = listOf(book("a"))
        assertTrue(ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Year(2025)).isEmpty())
        assertTrue(ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Rating(4.0)).isEmpty())
    }

    @Test
    fun `la nota se compara exacta, incluidas las medias estrellas`() {
        val enrichment = mapOf(
            "a" to BookEnrichment(bookId = "a", rating = 4.5),
            "b" to BookEnrichment(bookId = "b", rating = 4.0)
        )
        val books = listOf(book("a"), book("b"))
        assertEquals(
            listOf("a"),
            ShelfFiltering.apply(books, enrichment, ShelfFilter.Rating(4.5)).map { it.id }
        )
    }

    /**
     * El caso que motiva la clave de idioma: BookWyrm guarda el idioma como venga en la
     * edición, así que la misma estantería mezcla «Danish» y «Dansk». La gráfica los cuenta
     * como uno, y el recorte tiene que traer los dos libros.
     */
    @Test
    fun `un idioma recoge las dos grafias que agrupa el perfil`() {
        val books = listOf(
            book("a", languages = listOf("Danish")),
            book("b", languages = listOf("Dansk")),
            book("c", languages = listOf("English"))
        )
        val result = ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Language("Danish"))
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `un libro con varios idiomas entra por cualquiera de ellos`() {
        val books = listOf(book("a", languages = listOf("English", "Danish")))
        assertTrue(
            ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Language("Danish")).isNotEmpty()
        )
    }

    @Test
    fun `el formato se compara tal cual lo da BookWyrm`() {
        val books = listOf(
            book("a", format = "Hardcover"),
            book("b", format = "EBook"),
            book("c")
        )
        assertEquals(
            listOf("a"),
            ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Format("Hardcover")).map { it.id }
        )
    }

    /** Un formato vacío no debe recoger a todos los libros que tampoco tienen formato. */
    @Test
    fun `un formato vacio no recoge los libros sin formato`() {
        val books = listOf(book("a"), book("b", format = "  "))
        assertTrue(ShelfFiltering.apply(books, emptyMap(), ShelfFilter.Format("")).isEmpty())
    }

    @Test
    fun `el recorte conserva el orden de la estanteria`() {
        val enrichment = mapOf(
            "a" to BookEnrichment(bookId = "a", finished = "2025-01-01"),
            "b" to BookEnrichment(bookId = "b", finished = "2025-02-01"),
            "c" to BookEnrichment(bookId = "c", finished = "2025-03-01")
        )
        val books = listOf(book("c"), book("a"), book("b"))
        assertEquals(
            listOf("c", "a", "b"),
            ShelfFiltering.apply(books, enrichment, ShelfFilter.Year(2025)).map { it.id }
        )
    }

    @Test
    fun `una fecha estropeada no entra en ningun ano`() {
        val enrichment = mapOf("a" to BookEnrichment(bookId = "a", finished = "sin fecha"))
        assertFalse(
            ShelfFiltering.matches(book("a"), enrichment["a"], ShelfFilter.Year(2025))
        )
    }
}
