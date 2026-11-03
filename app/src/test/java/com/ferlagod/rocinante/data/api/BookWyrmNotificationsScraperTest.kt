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

import com.ferlagod.rocinante.data.model.AnnouncementDisplayType
import com.ferlagod.rocinante.data.model.NotificationType
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class BookWyrmNotificationsScraperTest {

    private fun createFakeApi(htmlResponse: String): BookWyrmApi {
        return Proxy.newProxyInstance(
            BookWyrmApi::class.java.classLoader,
            arrayOf(BookWyrmApi::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getRawHtmlResponse" -> Response.success(htmlResponse.toResponseBody())
                else -> null
            }
        } as BookWyrmApi
    }

    @Test
    fun `test scrape follow notification with avatar and local link`() = runBlocking {
        val html = """
            <html>
            <body>
                <div class="notification unread" id="notif-123">
                    <div class="avatar">
                        <a href="/user/ferlagog@lectura.social">
                            <img src="/images/avatars/avatar1.jpg" alt="ferlagod" />
                        </a>
                    </div>
                    <div class="content">
                        <strong><a href="/user/ferlagog@lectura.social">ferlagod</a></strong> started following you.
                    </div>
                    <time>2 hours ago</time>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val notifs = BookWyrmScraper.scrapeNotifications(fakeApi, "https://bookwyrm.it")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW, item.type)
        assertEquals("ferlagod", item.actorName)
        assertEquals("https://bookwyrm.it/images/avatars/avatar1.jpg", item.actorAvatarUrl)
        assertEquals("https://bookwyrm.it/user/ferlagog@lectura.social", item.permalink)
    }

    @Test
    fun `follow notification takes the name from the text link, not the avatar alt`() = runBlocking {
        // Tal cual lo sirve BookWyrm: primero el enlace del avatar (alt «avatar for …») y luego el
        // del nombre.
        val html = """
            <html>
            <body>
                <div class="notification ">
                    <div class="columns is-mobile has-text-more-muted">
                        <div class="column is-narrow is-size-3">
                            <a class="icon" href="/user/Rocinante/followers"><span class="icon icon-local"></span></a>
                        </div>
                        <div class="column is-clipped">
                            <div class="block content">
                                <a href="/user/ferlagod@frikiverse.zone" class="mr-2"><img
                                    class="avatar image is-32x32"
                                    src="/images/avatars/e850.png"
                                    alt="avatar for ferlagod@frikiverse.zone"></a>
                                <a href="/user/ferlagod@frikiverse.zone">ferlagod</a> ti segue
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val notifs = BookWyrmScraper.scrapeNotifications(createFakeApi(html), "https://bookwyrm.it")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW, item.type)
        assertEquals(1, item.relatedUsers.size)
        assertEquals("ferlagod", item.actorName)
        assertEquals("ferlagod", item.relatedUsers[0].name)
        assertEquals("@ferlagod@frikiverse.zone", item.relatedUsers[0].handle)
        assertEquals("https://bookwyrm.it/user/ferlagod@frikiverse.zone", item.relatedUsers[0].profileUrl)
        assertEquals("https://bookwyrm.it/images/avatars/e850.png", item.relatedUsers[0].avatarUrl)
    }

    @Test
    fun `spanish follow notification is recognised by its icon link`() = runBlocking {
        // «te ha seguido» es la traducción real de BookWyrm y no estaba entre los textos conocidos:
        // la notificación salía como desconocida y se fusionaba con la anterior.
        val html = """
            <div class="notification">
                <div class="columns is-mobile">
                    <div class="column is-narrow"><a class="icon" href="/user/yo/followers"><span class="icon icon-local"></span></a></div>
                    <div class="column is-clipped"><div class="block content">
                        <a href="/user/ana" class="mr-2"><img class="avatar" src="/a.png" alt="avatar de ana"></a>
                        <a href="/user/ana">Ana</a> te ha seguido
                    </div></div>
                </div>
            </div>
            <div class="notification">
                <div class="columns is-mobile">
                    <div class="column is-narrow"><a class="icon" href="/user/yo/followers"><span class="icon icon-local"></span></a></div>
                    <div class="column is-clipped"><div class="block content">
                        <a href="/user/luis" class="mr-2"><img class="avatar" src="/l.png" alt="avatar de luis"></a>
                        <a href="/user/luis">Luis</a> te ha enviado una solicitud de seguimiento
                    </div></div>
                </div>
            </div>
        """.trimIndent()

        val notifs = BookWyrmScraper.scrapeNotifications(createFakeApi(html), "https://bookwyrm.es")

        assertEquals(2, notifs.size)
        assertEquals(NotificationType.FOLLOW, notifs[0].type)
        assertEquals("Ana", notifs[0].actorName)
        assertEquals("https://bookwyrm.es/user/ana", notifs[0].permalink)
        // Solicitud ya atendida (sin botones): se abre como un seguidor normal.
        assertEquals(NotificationType.FOLLOW, notifs[1].type)
        assertEquals("Luis", notifs[1].actorName)
    }

    private fun statusNotification(icon: String, text: String, previewText: String, unread: Boolean = false) = """
        <div class="notification ${if (unread) "has-background-primary" else ""}">
            <div class="columns is-mobile">
                <div class="column is-narrow is-size-3">
                    <a class="icon" href="/user/yo/review/12"><span class="icon $icon"></span></a>
                </div>
                <div class="column is-clipped">
                    <div class="block content">
                        <a href="/user/ana" class="mr-2"><img class="avatar" src="/a.png" alt="avatar de ana"></a>
                        <a href="/user/ana">Ana</a> $text <a href="/user/yo/review/12">tu reseña de <em>Libro</em></a>
                    </div>
                    <div class="block">
                        <div class="notification py-2 has-background-body">
                            <div class="columns">
                                <div class="column is-clipped"><a href="/user/yo/review/12">$previewText</a></div>
                                <div class="column is-narrow">2 días</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    """

    @Test
    fun `spanish boost and mention are typed by their icon and keep their preview`() = runBlocking {
        val html = statusNotification("icon-boost", "ha impulsado", "Me gustó y lo recomiendo a quien me follow", unread = true) +
            statusNotification("icon-comment", "te ha mencionado en", "Hola @yo") +
            statusNotification("icon-heart", "le ha gustado", "Una reseña") +
            statusNotification("icon-comments", "ha respondido a", "Una respuesta")

        val notifs = BookWyrmScraper.scrapeNotifications(createFakeApi(html), "https://bookwyrm.es")

        // Cuatro, no ocho ni dos: la vista previa anidada no es otra notificación ni se fusiona.
        assertEquals(4, notifs.size)
        assertEquals(NotificationType.BOOST, notifs[0].type)
        assertEquals(NotificationType.MENTION, notifs[1].type)
        assertEquals(NotificationType.FAVORITE, notifs[2].type)
        assertEquals(NotificationType.REPLY, notifs[3].type)
        assertTrue(notifs[0].isUnread)
        assertFalse(notifs[1].isUnread)
        // El enlace a la reseña no es un usuario: solo Ana.
        assertEquals(listOf("Ana"), notifs[0].relatedUsers.map { it.name })
        assertEquals("Ana", notifs[0].actorName)
        assertTrue(notifs[0].content.contains("lo recomiendo"))
    }

    @Test
    fun `unknown notification with its own icon is not glued to the previous one`() = runBlocking {
        val html = statusNotification("icon-heart", "le ha gustado", "Una reseña") + """
            <div class="notification">
                <div class="columns is-mobile">
                    <div class="column is-narrow"><a class="icon" href="/import/5"><span class="icon icon-list"></span></a></div>
                    <div class="column is-clipped"><div class="block content">Tu importación ha terminado.</div></div>
                </div>
            </div>
        """

        val notifs = BookWyrmScraper.scrapeNotifications(createFakeApi(html), "https://bookwyrm.es")

        assertEquals(2, notifs.size)
        assertEquals(NotificationType.FAVORITE, notifs[0].type)
        assertEquals(NotificationType.UNKNOWN, notifs[1].type)
    }

    @Test
    fun `profile hrefs exclude a user's own pages`() {
        assertTrue(BookWyrmScraper.isProfileHref("/user/ana"))
        assertTrue(BookWyrmScraper.isProfileHref("/user/ana@otro.club/"))
        assertTrue(BookWyrmScraper.isProfileHref("https://mastodon.social/@jean"))
        assertTrue(BookWyrmScraper.isProfileHref("https://mastodon.social/users/jean"))
        assertFalse(BookWyrmScraper.isProfileHref("/user/ana/review/12"))
        assertFalse(BookWyrmScraper.isProfileHref("/user/ana/followers"))
        assertFalse(BookWyrmScraper.isProfileHref("https://mastodon.social/@jean/1234"))
        assertFalse(BookWyrmScraper.isProfileHref("/book/12"))
    }

    @Test
    fun `avatar alt with the username falls back to the username`() = runBlocking {
        val html = """
            <div class="notification">
                <a href="/user/lector"><img class="avatar" src="/a.png" alt="avatar de lector"></a>
                te ha seguido
            </div>
        """.trimIndent()

        val item = BookWyrmScraper.scrapeNotifications(createFakeApi(html), "https://bookwyrm.es").single()

        assertEquals("lector", item.relatedUsers.single().name)
    }

    @Test
    fun `test scrape follow request notification`() = runBlocking {
        val html = """
            <html>
            <body>
                <div class="notification" id="notif-456">
                    <div class="avatar">
                        <a href="/user/anotheruser">
                            <img src="https://remote.server/avatar.png" alt="Another User" />
                        </a>
                    </div>
                    <div class="content">
                        <strong>Another User</strong> requested to follow you.
                        <form action="/accept-follow-request" method="POST">
                            <input type="hidden" name="user" value="99" />
                        </form>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val notifs = BookWyrmScraper.scrapeNotifications(fakeApi, "https://bookwyrm.it")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW_REQUEST, item.type)
        assertEquals("Another User", item.actorName)
        assertEquals("https://remote.server/avatar.png", item.actorAvatarUrl)
        assertEquals("https://bookwyrm.it/user/anotheruser", item.permalink)
        assertEquals("99", item.formUserId)
    }

    @Test
    fun `test scrape spanish follow notification`() = runBlocking {
        val html = """
            <html>
            <body>
                <div class="notification unread" id="notif-789">
                    <div class="avatar">
                        <a href="/user/lector_voraz">
                            <img src="/images/avatars/avatar2.jpg" alt="Lector Voraz" />
                        </a>
                    </div>
                    <div class="content">
                        <strong><a href="/user/lector_voraz">Lector Voraz</a></strong> comenzó a seguirte.
                    </div>
                    <time>Hace 5 minutos</time>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val notifs = BookWyrmScraper.scrapeNotifications(fakeApi, "https://bookwyrm.es")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW, item.type)
        assertEquals("Lector Voraz", item.actorName)
        assertEquals("https://bookwyrm.es/user/lector_voraz", item.permalink)
    }

    @Test
    fun `test scrape federated follow request in french`() = runBlocking {
        val html = """
            <html>
            <body>
                <div class="notification" id="notif-101">
                    <div class="avatar">
                        <a href="https://mastodon.social/@jean">
                            <img src="https://mastodon.social/system/accounts/avatars/jean.png" alt="Jean" />
                        </a>
                    </div>
                    <div class="content">
                        <strong>Jean</strong> a demandé à vous suivre.
                        <form action="/accept-follow-request" method="POST">
                            <input type="hidden" name="user" value="jean@mastodon.social" />
                        </form>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val notifs = BookWyrmScraper.scrapeNotifications(fakeApi, "https://bookwyrm.fr")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW_REQUEST, item.type)
        assertEquals("Jean", item.actorName)
        assertEquals("https://mastodon.social/@jean", item.permalink)
        assertEquals("jean@mastodon.social", item.formUserId)
    }

    @Test
    fun `test scrape grouped follow notification with 2 users`() = runBlocking {
        val html = """
            <html>
            <body>
                <div class="notification" id="notif-grouped">
                    <div class="columns is-mobile">
                        <div class="column is-narrow is-size-3">
                            <a class="icon" href="/user/myuser/followers">
                                <span class="icon icon-local"></span>
                            </a>
                        </div>
                        <div class="column is-clipped">
                            <div class="block">
                                <ul class="is-flex">
                                    <li class="mr-2">
                                        <a href="/user/alice">
                                            <img class="avatar" src="/images/alice.jpg" alt="Alice" />
                                        </a>
                                    </li>
                                    <li class="mr-2">
                                        <a href="/user/bob@fediverse.org">
                                            <img class="avatar" src="https://fediverse.org/bob.png" alt="Bob" />
                                        </a>
                                    </li>
                                </ul>
                            </div>
                            <div class="block content">
                                <a href="/user/alice">Alice</a> and
                                <a href="/user/bob@fediverse.org">Bob</a> followed you
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val notifs = BookWyrmScraper.scrapeNotifications(fakeApi, "https://bookwyrm.social")

        assertEquals(1, notifs.size)
        val item = notifs[0]
        assertEquals(NotificationType.FOLLOW, item.type)
        assertEquals(2, item.relatedUsers.size)
        assertEquals("Alice", item.relatedUsers[0].name)
        assertEquals("https://bookwyrm.social/user/alice", item.relatedUsers[0].profileUrl)
        assertEquals("@alice", item.relatedUsers[0].handle)
        assertEquals("Bob", item.relatedUsers[1].name)
        assertEquals("https://bookwyrm.social/user/bob@fediverse.org", item.relatedUsers[1].profileUrl)
        assertEquals("@bob@fediverse.org", item.relatedUsers[1].handle)
    }

    @Test
    fun `test scrape server announcement with warning type, event date and content`() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <aside class="notification mb-1 p-3 is-hidden transition-y has-background-warning-light" data-hide="hide_announcement_42">
                    <details>
                        <summary>
                            <strong>Hoy:</strong>
                            Mantenimiento programado de la base de datos esta noche.
                            <span class="details-close mt-4 mr-4 icon icon-x is-small" aria-hidden></span>
                        </summary>
                        <div class="mb-2 mt-2" id="announcement_uuid123">
                            <div class="box is-shadowless mb-0">
                                <p>El servidor estará inactivo aproximadamente 15 minutos.</p>
                            </div>
                        </div>
                    </details>
                    <div class="is-flex mt-0 help">
                        <p>Publicado por <a href="/user/admin">Administrador</a></p>
                        <span class="mr-2 ml-2" aria-hidden="true">&middot;</span>
                        <a class="set-display" data-id="hide_announcement_42" data-value="true">Dismiss message</a>
                    </div>
                </aside>
                <div class="notification unread" id="notif-1">
                    <div class="content"><strong><a href="/user/alice">alice</a></strong> mentioned you</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val result = BookWyrmScraper.scrapeNotificationsResult(fakeApi, "https://comelibros.club")

        assertEquals(1, result.notifications.size)
        assertEquals(1, result.serverAnnouncements.size)

        val announcement = result.serverAnnouncements[0]
        assertEquals("42", announcement.id)
        assertEquals("Mantenimiento programado de la base de datos esta noche.", announcement.preview)
        assertEquals("Hoy", announcement.eventDate)
        assertEquals(AnnouncementDisplayType.WARNING, announcement.displayType)
        assertEquals("El servidor estará inactivo aproximadamente 15 minutos.", announcement.contentText)
        assertEquals("Administrador", announcement.postedByName)
        assertEquals("https://comelibros.club/user/admin", announcement.postedByUrl)
    }

    @Test
    fun `test scrape server announcement with danger type and fallback ID`() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <aside class="notification mb-1 p-3 has-background-danger-light" data-hide="hide_announcement_99">
                    <details>
                        <summary>
                            Caída inminente del servicio por corte eléctrico.
                        </summary>
                    </details>
                </aside>
            </body>
            </html>
        """.trimIndent()

        val fakeApi = createFakeApi(html)
        val result = BookWyrmScraper.scrapeNotificationsResult(fakeApi, "https://bookwyrm.it")

        assertEquals(0, result.notifications.size)
        assertEquals(1, result.serverAnnouncements.size)

        val announcement = result.serverAnnouncements[0]
        assertEquals("99", announcement.id)
        assertEquals("Caída inminente del servicio por corte eléctrico.", announcement.preview)
        assertEquals(null, announcement.eventDate)
        assertEquals(AnnouncementDisplayType.DANGER, announcement.displayType)
    }
}
