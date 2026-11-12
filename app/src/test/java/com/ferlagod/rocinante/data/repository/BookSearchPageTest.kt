/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchPageTest {

    private val base = "https://bookwyrm.example/"

    // Como search/book.html: un resultado local y dos catálogos externos, uno de ellos otra
    // instancia de BookWyrm cuyo enlace también lleva /book/.
    private val html = """
        <ul class="block">
          <li class="pd-4 mb-5 local-book-search-result" id="tour-local-book-search-result">
            <div class="columns is-mobile is-gapless mb-0">
              <div class="column is-cover"><img class="book-cover" src="/images/covers/1.jpg"></div>
              <div class="column is-10 ml-3">
                <p><strong><a href="/book/42/s/1984">1984</a> by <a class="author" href="/author/7">George Orwell</a></strong></p>
                <p>3 reviews (published 1949)</p>
              </div>
            </div>
          </li>
        </ul>
        <div class="block">
          <section class="mb-5">
            <details class="details-panel box" open>
              <summary class="is-flex remote-book-search-result" id="tour-remote-search-result">
                <span class="mb-0 title is-5">Results from <a href="https://inventaire.io">Inventaire</a></span>
              </summary>
              <div><div class="is-flex is-flex-direction-row-reverse"><ul class="is-flex-grow-1">
                <li class="mb-5">
                  <div class="columns is-mobile is-gapless">
                    <div class="column is-1 is-cover"><img class="book-cover" src="https://inventaire.io/img/x.jpg"></div>
                    <div class="column is-10 ml-3">
                      <p><strong><a href="https://inventaire.io/entity/isbn:9788834018842">Una mente in frammenti</a></strong></p>
                      <p>Gabor Maté (2019)</p>
                      <form class="mt-1" action="/resolve-book" method="post">
                        <input type="hidden" name="csrfmiddlewaretoken" value="t">
                        <input type="hidden" name="remote_id" value="https://inventaire.io/api/entities?action=by-uris&amp;uris=isbn:9788834018842">
                        <button type="submit">Import book</button>
                      </form>
                    </div>
                  </div>
                </li>
              </ul></div></div>
            </details>
          </section>
          <section class="mb-5">
            <details class="details-panel box" open>
              <summary class="is-flex remote-book-search-result"><span class="mb-0 title is-5">Results from <a href="https://otra.example">otra.example</a></span></summary>
              <div><ul class="is-flex-grow-1">
                <li>
                  <p><strong><a href="https://otra.example/book/99">Il nome della rosa</a></strong></p>
                  <p>Umberto Eco</p>
                  <form action="/resolve-book" method="post"><input type="hidden" name="remote_id" value="https://otra.example/book/99"></form>
                </li>
              </ul></div>
            </details>
          </section>
        </div>
    """.trimIndent()

    @Test
    fun `lee locales y remotos con su catalogo`() {
        val results = SearchRepository.parseBookSearchPage(html, base)
        assertEquals(3, results.size)

        val local = results[0]
        assertFalse(local.isRemote)
        assertEquals("42", local.key)
        assertEquals("1984", local.title)
        assertEquals("George Orwell", local.author)
        assertEquals(1949, local.year)
        assertEquals("https://bookwyrm.example/images/covers/1.jpg", local.cover)

        val inventaire = results[1]
        assertTrue(inventaire.isRemote)
        assertEquals("Inventaire", inventaire.source)
        assertEquals("Una mente in frammenti", inventaire.title)
        assertEquals("Gabor Maté", inventaire.author)
        assertEquals(2019, inventaire.year)
        assertEquals("https://inventaire.io/img/x.jpg", inventaire.cover)
        assertTrue(inventaire.remoteId!!.startsWith("https://inventaire.io/api/entities"))
    }

    @Test
    fun `un libro de otra instancia no se toma por local`() {
        val results = SearchRepository.parseBookSearchPage(html, base)
        assertTrue(results.none { !it.isRemote && it.key == "99" })
        val otra = results.single { it.remoteId == "https://otra.example/book/99" }
        assertEquals("otra.example", otra.source)
        assertEquals("Umberto Eco", otra.author)
        assertEquals(null, otra.year)
    }

    @Test
    fun `sin nombre de catalogo se usa el dominio`() {
        val sinNombre = """
            <section><details><summary></summary><ul><li>
              <p><strong>Libro</strong></p>
              <form action="/resolve-book"><input name="remote_id" value="https://www.openlibrary.org/books/OL1M"></form>
            </li></ul></details></section>
        """.trimIndent()
        assertEquals("openlibrary.org", SearchRepository.parseBookSearchPage(sinNombre, base).single().source)
    }
}
