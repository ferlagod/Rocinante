package com.ferlagod.rocinante.data.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {

    @Test
    fun `inicializa cookies y extrae csrf correctamente`() {
        val jar = SessionCookieJar("csrftoken=test_token_123; sessionid=sess_456", "bookwyrm.it")
        assertEquals("test_token_123", jar.currentCsrfToken())
        assertTrue(jar.asCookieString().contains("sessionid=sess_456"))
    }

    @Test
    fun `rota el csrf al recibir set-cookie del servidor`() {
        var updatedCookies: String? = null
        val jar = SessionCookieJar("csrftoken=old_token; sessionid=sess_1", "bookwyrm.it") {
            updatedCookies = it
        }

        val url = "https://bookwyrm.it/shelve/".toHttpUrl()
        val newCookies = listOf(
            Cookie.Builder().name("csrftoken").value("new_rotated_token").domain("bookwyrm.it").build()
        )

        jar.saveFromResponse(url, newCookies)

        assertEquals("new_rotated_token", jar.currentCsrfToken())
        assertTrue(jar.asCookieString().contains("csrftoken=new_rotated_token"))
        assertTrue(jar.asCookieString().contains("sessionid=sess_1"))
        assertEquals(jar.asCookieString(), updatedCookies)
    }

    @Test
    fun `no envia cookies a hosts de terceros pero si al host de la instancia`() {
        val jar = SessionCookieJar("csrftoken=my_csrf; sessionid=my_sess", "bookwyrm.it")

        val ownUrl = "https://bookwyrm.it/user/test.json".toHttpUrl()
        val cookiesForOwn = jar.loadForRequest(ownUrl)
        assertEquals(2, cookiesForOwn.size)

        val remoteUrl = "https://openlibrary.org/works/OL123.json".toHttpUrl()
        val cookiesForRemote = jar.loadForRequest(remoteUrl)
        assertTrue(cookiesForRemote.isEmpty())
    }

    @Test
    fun `merge combina cookies nuevas sin borrar las existentes`() {
        val jar = SessionCookieJar("sessionid=keep_me; csrftoken=old_csrf", "comelibros.club")
        jar.merge("techaro.lol-anubis-auth=cleared_anubis; csrftoken=new_csrf")

        val result = jar.asCookieString()
        assertTrue(result.contains("sessionid=keep_me"))
        assertTrue(result.contains("techaro.lol-anubis-auth=cleared_anubis"))
        assertEquals("new_csrf", jar.currentCsrfToken())
    }

    @Test
    fun `ignora atributos de cookie como Path y Expires y borra valores vacios`() {
        val jar = SessionCookieJar("sessionid=keep_me; to_delete=old_val", "comelibros.club")
        jar.merge("to_delete=; Path=/; Expires=Mon, 14 Sep 2026 12:32:13 GMT; SameSite=None; new_cookie=val123")

        val cookies = jar.loadForRequest("https://comelibros.club/feed".toHttpUrl())
        val names = cookies.map { it.name }
        assertTrue(names.contains("sessionid"))
        assertTrue(names.contains("new_cookie"))
        assertFalse(names.contains("to_delete"))
        assertFalse(names.contains("Path"))
        assertFalse(names.contains("Expires"))
        assertFalse(names.contains("SameSite"))
    }
}
