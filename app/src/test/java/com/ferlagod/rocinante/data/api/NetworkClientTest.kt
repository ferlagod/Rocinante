package com.ferlagod.rocinante.data.api

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    @Test
    fun `reconoce la redireccion al login tanto relativa como absoluta`() {
        val req = "https://bookwyrm.it/shelve/".toHttpUrl()
        assertTrue(NetworkClient.isLoginLocation("/login?next=/shelve/", req))
        assertTrue(NetworkClient.isLoginLocation("/login/", req))
        assertTrue(NetworkClient.isLoginLocation("https://bookwyrm.it/login?next=/", null))
        assertFalse(NetworkClient.isLoginLocation("/book/12", req))
        assertFalse(NetworkClient.isLoginLocation("/user/loginator/shelf/read", req))
        assertFalse(NetworkClient.isLoginLocation(null, req))
    }

    @Test
    fun `solo la instancia y sus subdominios cuentan como propios`() {
        assertTrue(NetworkClient.isInstanceHost("bookwyrm.social", "bookwyrm.social"))
        assertTrue(NetworkClient.isInstanceHost("www.bookwyrm.social", "https://bookwyrm.social/"))
        assertTrue(NetworkClient.isInstanceHost("cdn.bookwyrm.social", "bookwyrm.social"))
        assertFalse(NetworkClient.isInstanceHost("bookwyrm.social.evil.com", "bookwyrm.social"))
        assertFalse(NetworkClient.isInstanceHost("social", "bookwyrm.social"))
        assertFalse(NetworkClient.isInstanceHost("bookwyrm.social", ""))
        assertFalse(NetworkClient.isInstanceHost("bookwyrm.social", null))
    }

    @Test
    fun `rellena el token csrf del formulario sin pisar uno existente`() {
        val sinToken = FormBody.Builder().add("book", "1").build()
        val conVacio = FormBody.Builder().add("csrfmiddlewaretoken", "").add("book", "1").build()
        val conToken = FormBody.Builder().add("csrfmiddlewaretoken", "enmascarado").build()

        val a = NetworkClient.formWithCsrf(sinToken, "secreto")!!
        assertEquals("secreto", (0 until a.size).first { a.name(it) == "csrfmiddlewaretoken" }.let { a.value(it) })
        val b = NetworkClient.formWithCsrf(conVacio, "secreto")!!
        assertEquals("secreto", b.value(0))
        val c = NetworkClient.formWithCsrf(conToken, "secreto")!!
        assertEquals("enmascarado", c.value(0))
        assertNull(NetworkClient.formWithCsrf("{}".toRequestBody("application/json".toMediaType()), "secreto"))
    }
}
