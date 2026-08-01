package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalShelfSearchTest {

    private fun book(id: String, title: String, sortTitle: String? = null) =
        ShelfBookItem(id = id, title = title, sortTitle = sortTitle, cover = null)

    private val reading = listOf(
        book("b1", "1984", "1984"),
        book("b2", "El nombre de la rosa", "nombre de la rosa, El")
    )
    private val toRead = listOf(
        book("b3", "Cumbres borrascosas"),
        book("b4", "Rebelión en la granja")
    )
    private val read = listOf(
        book("b5", "La casa de los espíritus"),
        book("b6", "Søndergaards hus")
    )

    private val shelves = mapOf(
        "reading" to reading,
        "to-read" to toRead,
        "read" to read
    )

    private val enrichment = mapOf(
        "b1" to BookEnrichment(bookId = "b1", authorName = "George Orwell"),
        "b4" to BookEnrichment(bookId = "b4", authorName = "George Orwell"),
        "b3" to BookEnrichment(bookId = "b3", authorName = "Emily Brontë")
    )

    @Test
    fun `encuentra por título sin distinguir mayúsculas`() {
        val hits = LocalShelfSearch.search("REBELIÓN", shelves, enrichment)
        assertEquals(listOf("b4"), hits.map { it.book.id })
        assertEquals("to-read", hits.first().shelfSlug)
    }

    @Test
    fun `ignora los acentos en la consulta y en el título`() {
        assertEquals(listOf("b4"), LocalShelfSearch.search("rebelion", shelves, enrichment).map { it.book.id })
        assertEquals(listOf("b3"), LocalShelfSearch.search("bronte", shelves, enrichment).map { it.book.id })
    }

    @Test
    fun `pliega las letras que el Unicode no descompone`() {
        assertEquals(listOf("b6"), LocalShelfSearch.search("sondergaard", shelves, enrichment).map { it.book.id })
    }

    @Test
    fun `busca también por el nombre del autor`() {
        val hits = LocalShelfSearch.search("orwell", shelves, enrichment)
        // Los dos libros de Orwell, con "Leyendo" antes que "Por leer".
        assertEquals(listOf("b1", "b4"), hits.map { it.book.id })
    }

    @Test
    fun `exige todas las palabras aunque vengan de campos distintos`() {
        assertEquals(listOf("b1"), LocalShelfSearch.search("orwell 1984", shelves, enrichment).map { it.book.id })
        assertTrue(LocalShelfSearch.search("orwell rosa", shelves, enrichment).isEmpty())
    }

    @Test
    fun `lo que empieza por la consulta va antes que lo que solo la contiene`() {
        val hits = LocalShelfSearch.search("la", shelves, enrichment)
        // "La casa de los espíritus" empieza por "la"; los demás solo la contienen.
        assertEquals("b5", hits.first().book.id)
    }

    @Test
    fun `una sola letra no busca`() {
        assertTrue(LocalShelfSearch.search("a", shelves, enrichment).isEmpty())
        assertTrue(LocalShelfSearch.search("", shelves, enrichment).isEmpty())
        assertTrue(LocalShelfSearch.search("   ", shelves, enrichment).isEmpty())
    }

    @Test
    fun `sin datos enriquecidos sigue buscando por título`() {
        val hits = LocalShelfSearch.search("cumbres", shelves)
        assertEquals(listOf("b3"), hits.map { it.book.id })
        assertEquals(null, hits.first().authorName)
    }

    @Test
    fun `un libro presente en dos estanterías aparece una sola vez`() {
        val duplicated = shelves + ("read" to (read + book("b1", "1984")))
        val hits = LocalShelfSearch.search("1984", duplicated, enrichment)
        assertEquals(1, hits.size)
        // Gana "Leyendo", que va antes en el orden de estanterías.
        assertEquals("reading", hits.first().shelfSlug)
    }

    @Test
    fun `devuelve todas las coincidencias sin recortarlas`() {
        val many = (1..40).map { book("m$it", "Tomo $it de la saga") }
        assertEquals(40, LocalShelfSearch.search("saga", mapOf("read" to many)).size)
    }

    @Test
    fun `el índice reutilizado da el mismo resultado que el atajo`() {
        val index = LocalShelfSearch.buildIndex(shelves, enrichment)
        assertEquals(
            LocalShelfSearch.search("orwell", shelves, enrichment).map { it.book.id },
            LocalShelfSearch.search("orwell", index).map { it.book.id }
        )
    }

    @Test
    fun `estanterías vacías o desconocidas no rompen la búsqueda`() {
        assertTrue(LocalShelfSearch.search("orwell", emptyMap()).isEmpty())
        val odd = mapOf("stopped-reading" to listOf(book("x1", "Ulises")))
        assertEquals(listOf("x1"), LocalShelfSearch.search("ulises", odd).map { it.book.id })
    }
}
