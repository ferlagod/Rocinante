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

    @Test
    fun `las cookies de bookwyrm social no salen hacia otros servidores`() {
        val jar = SessionCookieJar("csrftoken=c; sessionid=s", "bookwyrm.social")
        assertEquals(2, jar.loadForRequest("https://bookwyrm.social/feed".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://covers.openlibrary.org/b/id/1-L.jpg".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://otra.instancia/book/1".toHttpUrl()).isEmpty())
    }

    @Test
    fun `un host que solo contiene el de la instancia no recibe cookies`() {
        val jar = SessionCookieJar("sessionid=s", "bookwyrm.it")
        assertTrue(jar.loadForRequest("https://bookwyrm.it.evil.com/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://notbookwyrm.it/".toHttpUrl()).isEmpty())
        // Subdominios y www sí son de la instancia.
        assertEquals(1, jar.loadForRequest("https://www.bookwyrm.it/".toHttpUrl()).size)
        assertEquals(1, jar.loadForRequest("https://static.bookwyrm.it/x.css".toHttpUrl()).size)
    }

    @Test
    fun `sin instancia conocida no se envia ninguna cookie`() {
        val jar = SessionCookieJar("sessionid=s", "")
        assertTrue(jar.loadForRequest("https://bookwyrm.social/".toHttpUrl()).isEmpty())
    }

    @Test
    fun `el csrftoken de otra instancia no pisa el del usuario`() {
        val jar = SessionCookieJar("csrftoken=mio; sessionid=s", "bookwyrm.it")
        val remote = "https://otra.instancia/book/1".toHttpUrl()
        jar.saveFromResponse(remote, listOf(
            Cookie.Builder().name("csrftoken").value("ajeno").domain("otra.instancia").build()
        ))
        assertEquals("mio", jar.currentCsrfToken())
    }

    @Test
    fun `volver a sincronizar la misma sesion no deshace un csrf rotado`() {
        val saved = "csrftoken=viejo; sessionid=s"
        val jar = SessionCookieJar(saved, "bookwyrm.it")
        jar.syncSession("bookwyrm.it", saved)
        jar.saveFromResponse("https://bookwyrm.it/shelve/".toHttpUrl(), listOf(
            Cookie.Builder().name("csrftoken").value("nuevo").domain("bookwyrm.it").build()
        ))
        // La sesión guardada aún no se ha actualizado en disco: sigue siendo la vieja.
        jar.syncSession("bookwyrm.it", saved)
        assertEquals("nuevo", jar.currentCsrfToken())
    }

    @Test
    fun `cambiar de instancia limpia las cookies de la anterior`() {
        val jar = SessionCookieJar("sessionid=a", "a.social")
        jar.syncSession("a.social", "sessionid=a")
        jar.syncSession("b.social", "csrftoken=b")
        assertFalse(jar.asCookieString().contains("sessionid=a"))
        assertEquals("b", jar.currentCsrfToken())
    }
}
