package com.ferlagod.rocinante.data.api

import com.ferlagod.rocinante.data.repository.InteractionRepository
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que la app espera del servidor de BookWyrm en sus respuestas y en sus páginas. */
class ServerContractTest {

    private fun post(accept: String? = "application/activity+json, application/json") =
        Request.Builder()
            .url("https://example.org/favorite/12/")
            .post(FormBody.Builder().add("csrfmiddlewaretoken", "x").build())
            .apply { if (accept != null) header("Accept", accept) }
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

    private fun responseTo(request: Request, code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .build()

    @Test
    fun `un envío rechazado por ir como JSON se repite pidiendo HTML`() {
        val sent = post()
        val retry = NetworkClient.htmlRetryRequest(sent, responseTo(sent, 403), isToInstance = true)
        assertNotNull(retry)
        assertEquals("text/html", retry!!.header("Accept"))
        assertNull(retry.header("X-Requested-With"))
        assertEquals("POST", retry.method)
    }

    @Test
    fun `no se repiten lecturas, otros servidores ni otras respuestas`() {
        val sent = post()
        assertNull(NetworkClient.htmlRetryRequest(sent, responseTo(sent, 403), isToInstance = false))
        assertNull(NetworkClient.htmlRetryRequest(sent, responseTo(sent, 400), isToInstance = true))
        val asHtml = post(accept = "text/html")
        assertNull(NetworkClient.htmlRetryRequest(asHtml, responseTo(asHtml, 403), isToInstance = true))
        val get = Request.Builder().url("https://example.org/user/a.json")
            .header("Accept", "application/json").build()
        assertNull(NetworkClient.htmlRetryRequest(get, responseTo(get, 403), isToInstance = true))
        val activity = Request.Builder().url("https://example.org/user/a/outbox")
            .header("Accept", "application/activity+json")
            .post("{}".toRequestBody("application/activity+json".toMediaType())).build()
        assertNull(NetworkClient.htmlRetryRequest(activity, responseTo(activity, 403), isToInstance = true))
    }

    @Test
    fun `una redirección fuera de la instancia no lleva la sesión ni el cuerpo`() {
        val get = Request.Builder().url("https://example.org/images/a.jpg")
            .header("X-CSRFToken", "t").header("Origin", "https://example.org").build()
        val out = NetworkClient.redirectRequest(get, "https://cdn.other.net/a.jpg".toHttpUrl(), "example.org")
        assertNotNull(out)
        assertNull(out!!.header("X-CSRFToken"))
        assertNull(out.header("Origin"))
        assertNull(NetworkClient.redirectRequest(post(), "https://other.net/x".toHttpUrl(), "example.org"))
        assertNotNull(NetworkClient.redirectRequest(post(), "https://example.org/y".toHttpUrl(), "example.org"))
    }

    @Test
    fun `reconoce las direcciones de todos los tipos de estado`() {
        listOf("status", "review", "reviewrating", "comment", "quotation", "boost", "generatednote")
            .forEach { type ->
                val match = InteractionRepository.STATUS_PATH.find("https://e.org/user/ana/$type/42#anchor-42")
                assertEquals(type, "42", match?.groupValues?.get(2))
            }
        assertNull(InteractionRepository.STATUS_PATH.find("https://e.org/post/42"))
    }

    @Test
    fun `una difusión se reconoce sin depender del idioma`() {
        val boost = org.jsoup.Jsoup.parse(
            """<div class="block"><a href="/user/ana">Ana</a> difundió
               <div class="card"><a href="/user/luis">Luis</a><p>texto</p></div></div>"""
        ).selectFirst("div.block")!!
        val normal = org.jsoup.Jsoup.parse(
            """<div class="block"><div class="card"><a href="/user/luis">Luis</a><p>texto</p></div></div>"""
        ).selectFirst("div.block")!!
        assertTrue(BookWyrmScraper.isBoostCard(boost))
        assertFalse(BookWyrmScraper.isBoostCard(normal))
    }
}
