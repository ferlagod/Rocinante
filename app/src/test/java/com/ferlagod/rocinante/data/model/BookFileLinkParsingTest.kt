package com.ferlagod.rocinante.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los dos campos nuevos del Edition se leen con Gson por el nombre, así que un cambio de
 * nombre o de forma no daría error: dejaría el campo en null y la ficha simplemente no
 * enseñaría nada. Estas pruebas usan la respuesta tal cual la da bookwyrm.social.
 */
class BookFileLinkParsingTest {

    private val gson = Gson()

    /** Recortado de https://bookwyrm.social/book/…json de «Dracula». */
    private val draculaJson = """
        {
          "title": "Dracula",
          "description": "",
          "publishedDate": "",
          "pages": 418,
          "cover": null,
          "isbn13": "9780486411095",
          "inventaireId": "isbn:9780486411095",
          "fileLinks": [
            {
              "href": "https://standardebooks.org/ebooks/bram-stoker/dracula",
              "mediaType": "ePub",
              "attributedTo": "https://bookrastinating.com/user/joel",
              "availability": "free"
            },
            {
              "href": "https://www.gutenberg.org/ebooks/345",
              "mediaType": "ePub",
              "attributedTo": "https://bookrastinating.com/user/joel",
              "availability": "free"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `se leen los enlaces a copias con sus datos`() {
        val book = gson.fromJson(draculaJson, BookWyrmBookDetails::class.java)
        val links = book.fileLinks.orEmpty()
        assertEquals(2, links.size)
        assertEquals("https://standardebooks.org/ebooks/bram-stoker/dracula", links[0].href)
        assertEquals("ePub", links[0].mediaType)
        assertEquals("free", links[0].availability)
    }

    @Test
    fun `se lee el identificador de Inventaire`() {
        val book = gson.fromJson(draculaJson, BookWyrmBookDetails::class.java)
        assertEquals("isbn:9780486411095", book.inventaireId)
    }

    /**
     * Lo corriente es que un libro no tenga ni lo uno ni lo otro: cuatro de cada cinco no
     * traen el identificador y casi ninguno trae enlaces. Eso no debe reventar la ficha.
     */
    @Test
    fun `un libro sin ninguno de los dos se lee igual`() {
        val book = gson.fromJson(
            """{"title":"Kvinden i buret","description":"","publishedDate":"","pages":378,"cover":null}""",
            BookWyrmBookDetails::class.java
        )
        assertEquals("Kvinden i buret", book.title)
        assertNull(book.inventaireId)
        assertTrue(book.fileLinks.isNullOrEmpty())
    }

    /** BookWyrm devuelve la lista vacía cuando nadie ha enlazado nada. */
    @Test
    fun `una lista vacia no es un enlace`() {
        val book = gson.fromJson(
            """{"title":"X","description":"","publishedDate":"","pages":1,"cover":null,"fileLinks":[]}""",
            BookWyrmBookDetails::class.java
        )
        assertTrue(book.fileLinks.orEmpty().isEmpty())
    }
}
