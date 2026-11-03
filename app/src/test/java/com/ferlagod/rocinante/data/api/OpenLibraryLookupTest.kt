/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenLibraryLookupTest {

    @Test
    fun `lee titulo autores paginas editorial y portada`() {
        val json = """
            {"ISBN:9788445071878": {
              "title": "Buenos presagios", "subtitle": "",
              "authors": [{"url": "x", "name": "Terry Pratchett"}, {"name": "Neil Gaiman"}],
              "number_of_pages": 384, "publish_date": "May 2003",
              "publishers": [{"name": "Minotauro"}],
              "cover": {"small": "https://covers.openlibrary.org/b/id/1-S.jpg", "large": "https://covers.openlibrary.org/b/id/1-L.jpg"}
            }}
        """.trimIndent()
        val data = OpenLibraryLookup.parse(json, "9788445071878")!!
        assertEquals("Buenos presagios", data.title)
        assertEquals(listOf("Terry Pratchett", "Neil Gaiman"), data.authors)
        assertEquals(384, data.pages)
        assertEquals(listOf("Minotauro"), data.publishers)
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", data.coverUrl)
        assertEquals("2003", OpenLibraryLookup.isoDateOrYear(data.publishedDate))
    }

    @Test
    fun `sin el libro devuelve null`() {
        assertNull(OpenLibraryLookup.parse("{}", "9788445071878"))
        assertNull(OpenLibraryLookup.parse("no es json", "9788445071878"))
    }

    @Test
    fun `fechas`() {
        assertEquals("2003-05-01", OpenLibraryLookup.isoDateOrYear("2003-05-01"))
        assertEquals("1999", OpenLibraryLookup.isoDateOrYear("1999"))
        assertEquals("", OpenLibraryLookup.isoDateOrYear("s.f."))
    }
}
