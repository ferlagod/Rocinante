package com.ferlagod.rocinante.data.api

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnubisClearanceTest {

    private fun response(
        url: String,
        code: Int,
        location: String? = null
    ): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .body("".toResponseBody(null))
        if (location != null) builder.header("Location", location)
        return builder.build()
    }

    @Test
    fun `un 307 hacia el reto se reconoce`() {
        val r = response(
            "https://bookwyrm.social/book/140643.json",
            307,
            "/.within.website/?redir=/book/140643.json"
        )
        assertTrue(AnubisClearance.isChallengeRedirect(r))
        assertTrue(AnubisClearance.isChallenge(r))
    }

    @Test
    fun `haber acabado dentro del reto también cuenta`() {
        // El cliente siguió la redirección por su cuenta: 200, pero es la página del reto.
        val r = response("https://bookwyrm.social/.within.website/?redir=/book/1.json", 200)
        assertFalse(AnubisClearance.isChallengeRedirect(r))
        assertTrue(AnubisClearance.isChallenge(r))
    }

    @Test
    fun `una respuesta normal no se confunde con el reto`() {
        assertFalse(AnubisClearance.isChallenge(response("https://bookwyrm.social/book/1.json", 200)))
    }

    @Test
    fun `una redirección que no es de Anubis se deja pasar`() {
        val r = response("https://bookwyrm.social/book/1", 308, "/book/1/")
        assertFalse(AnubisClearance.isChallenge(r))
    }

    @Test
    fun `extrae la cookie de paso de la cadena de cookies`() {
        val cookies = "csrftoken=abc; techaro.lol-anubis-auth=eyJhbGciOi.firma; sessionid=xyz"
        assertEquals("eyJhbGciOi.firma", AnubisClearance.tokenOf(cookies))
    }

    @Test
    fun `sin cookie de paso devuelve null`() {
        assertNull(AnubisClearance.tokenOf("csrftoken=abc; sessionid=xyz"))
        assertNull(AnubisClearance.tokenOf(""))
        assertNull(AnubisClearance.tokenOf(null))
        // Presente pero vacía: tampoco sirve para nada.
        assertNull(AnubisClearance.tokenOf("techaro.lol-anubis-auth=; sessionid=xyz"))
    }

    @Test
    fun `no confunde una cookie cuyo nombre acaba igual`() {
        assertNull(AnubisClearance.tokenOf("otra-techaro.lol-anubis-auth=valor"))
    }

    @Test
    fun `normaliza la instancia a la clave del almacén de cookies`() {
        assertEquals("https://bookwyrm.social/", AnubisClearance.baseUrlOf("bookwyrm.social"))
        assertEquals("https://bookwyrm.social/", AnubisClearance.baseUrlOf("https://bookwyrm.social"))
        assertEquals("https://bookwyrm.social/", AnubisClearance.baseUrlOf("https://bookwyrm.social/"))
        assertNull(AnubisClearance.baseUrlOf(null))
        assertNull(AnubisClearance.baseUrlOf("  "))
    }
}
