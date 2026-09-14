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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BookWyrmScraperFallbackTest {

    @Test
    fun `scrapeUserProfileFromHtml extrae perfil correctamente`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Rocinante (@Rocinante@comelibros.club) - BookWyrm</title>
                <meta property="og:title" content="Rocinante (@Rocinante@comelibros.club)" />
                <meta property="og:description" content="Amante de los clásicos y la aventura." />
                <meta property="og:image" content="/images/avatars/user.png" />
            </head>
            <body>
                <header>
                    <a href="/user/Rocinante/followers">10 followers</a>
                    <a href="/user/Rocinante/following">5 following</a>
                </header>
                <div class="user-summary">Amante de los clásicos y la aventura.</div>
            </body>
            </html>
        """.trimIndent()

        val profile = BookWyrmScraper.scrapeUserProfileFromHtml(html, "Rocinante", "https://comelibros.club")
        assertEquals("https://comelibros.club/user/Rocinante", profile.id)
        assertEquals("Rocinante", profile.name)
        assertEquals("Rocinante", profile.preferredUsername)
        assertEquals("Amante de los clásicos y la aventura.", profile.summary)
        assertEquals("https://comelibros.club/images/avatars/user.png", profile.icon?.url)
        assertEquals("https://comelibros.club/user/Rocinante/outbox", profile.outbox)
        assertEquals("https://comelibros.club/user/Rocinante/inbox", profile.inbox)
        assertEquals("https://comelibros.club/user/Rocinante/followers", profile.followers)
        assertEquals("https://comelibros.club/user/Rocinante/following", profile.following)
        assertEquals(10, profile.followersCountLocal)
        assertEquals(5, profile.followingCountLocal)
    }

    @Test
    fun `scrapeUserProfileFromHtml extrae bio con preserve-whitespace y contadores en 0`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="ComeLibros Club" />
                <meta property="og:description" content="None" />
            </head>
            <body>
                <div class="media block">
                    <div class="media-left">
                        <a href="/user/Rocinante"><img class="avatar" src="/images/avatars/ab64.png" /></a>
                    </div>
                    <div class="media-content">
                        <p>Rocinante</p>
                        <p><a href="https://comelibros.club/user/Rocinante">Rocinante@comelibros.club</a></p>
                        <p><a href="/user/Rocinante/followers">0 followers</a>, <a href="/user/Rocinante/following">0 following</a></p>
                    </div>
                </div>
                <div class="column box has-background-tertiary content preserve-whitespace">
                    <p>Cliente de Bookwyrm para android</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val profile = BookWyrmScraper.scrapeUserProfileFromHtml(html, "Rocinante", "https://comelibros.club")
        assertEquals("Rocinante", profile.name)
        assertEquals("Cliente de Bookwyrm para android", profile.summary)
        assertEquals(0, profile.followersCountLocal)
        assertEquals(0, profile.followingCountLocal)
        assertEquals("https://comelibros.club/images/avatars/ab64.png", profile.icon?.url)
    }

    @Test
    fun `scrapeShelfPageFromHtml extrae lista de libros correctamente`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="book-row">
                    <a href="/book/42">
                        <img src="/images/covers/quijote.jpg" />
                        <span class="title">Don Quijote de la Mancha</span>
                    </a>
                    <a href="/author/1">Miguel de Cervantes</a>
                    <span>450 páginas</span>
                </div>
            </body>
            </html>
        """.trimIndent()

        val items = BookWyrmScraper.scrapeShelfPageFromHtml(html, "https://comelibros.club")
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("https://comelibros.club/book/42", item.id)
        assertEquals("Don Quijote de la Mancha", item.title)
        assertEquals("https://comelibros.club/images/covers/quijote.jpg", item.cover?.url)
        assertEquals(450, item.pages)
        assertEquals(listOf("https://comelibros.club/author/1"), item.authors)
    }

    @Test
    fun `scrapeBookDetailsFromHtml extrae detalles de un libro`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Cien años de soledad | BookWyrm</title>
                <meta property="og:title" content="Cien años de soledad" />
                <meta property="og:description" content="Muchos años después, frente al pelotón de fusilamiento..." />
                <meta property="og:image" content="https://comelibros.club/media/covers/soledad.jpg" />
                <meta name="DC.Date" content="1967-05-30" />
            </head>
            <body>
                <h1>Cien años de soledad</h1>
                <div class="subtitle">Edición conmemorativa</div>
                <div class="book-description">Muchos años después, frente al pelotón de fusilamiento...</div>
                <a class="author" href="/author/gabriel-garcia-marquez"><span itemprop="name">Gabriel García Márquez</span></a>
                <span itemprop="numberOfPages">471 páginas</span>
                <img class="cover" src="/media/covers/soledad.jpg" />
            </body>
            </html>
        """.trimIndent()

        val details = BookWyrmScraper.scrapeBookDetailsFromHtml(html, "https://comelibros.club/book/100", "https://comelibros.club")
        assertEquals("Cien años de soledad", details.title)
        assertEquals("Edición conmemorativa", details.subtitle)
        assertEquals("Muchos años después, frente al pelotón de fusilamiento...", details.description)
        assertEquals("1967-05-30", details.publishedDate)
        assertEquals(471, details.pages)
        assertEquals("https://comelibros.club/media/covers/soledad.jpg", details.cover?.url)
        assertEquals(listOf("https://comelibros.club/author/gabriel-garcia-marquez"), details.authors)
    }
}
