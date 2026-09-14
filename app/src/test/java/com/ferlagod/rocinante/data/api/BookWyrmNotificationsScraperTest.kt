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

import com.ferlagod.rocinante.data.model.NotificationType
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
}
