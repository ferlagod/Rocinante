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

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Al seguir una redirección 307/308, la sesión no debe salir de la instancia. */
class RedirectRequestTest {

    private val instance = "bookwyrm.example"

    private fun get(url: String) = Request.Builder()
        .url(url)
        .header("X-CSRFToken", "secreto")
        .header("Origin", "https://$instance")
        .header("Referer", "https://$instance/")
        .header("User-Agent", "Rocinante")
        .build()

    @Test
    fun `dentro de la instancia se conservan las cabeceras`() {
        val next = NetworkClient.redirectRequest(
            get("https://$instance/book/1"), "https://$instance/book/1/s/titulo".toHttpUrl(), instance
        )!!
        assertEquals("secreto", next.header("X-CSRFToken"))
        assertEquals("https://$instance", next.header("Origin"))
    }

    @Test
    fun `hacia otro servidor se quitan las cabeceras de la sesión`() {
        val next = NetworkClient.redirectRequest(
            get("https://$instance/images/cover.jpg"), "https://s3.example/cover.jpg".toHttpUrl(), instance
        )!!
        assertEquals("https://s3.example/cover.jpg", next.url.toString())
        assertNull(next.header("X-CSRFToken"))
        assertNull(next.header("Origin"))
        assertNull(next.header("Referer"))
        assertEquals("Rocinante", next.header("User-Agent"))
    }

    @Test
    fun `un POST no se sigue fuera de la instancia`() {
        val post = Request.Builder()
            .url("https://$instance/shelve/")
            .post(FormBody.Builder().add("csrfmiddlewaretoken", "secreto").build())
            .build()
        assertNull(NetworkClient.redirectRequest(post, "https://otro.example/shelve/".toHttpUrl(), instance))
    }
}
