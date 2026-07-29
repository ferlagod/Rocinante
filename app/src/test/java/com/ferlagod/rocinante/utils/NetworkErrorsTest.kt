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
package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas de [NetworkErrors.classify]: la parte que decide de quién es el fallo no toca
 * Android, así que corre en la JVM (`./gradlew :app:testDebugUnitTest`).
 */
class NetworkErrorsTest {

    // --- excepciones ---

    @Test
    fun `la instancia que no contesta a tiempo es un timeout`() {
        assertEquals(
            NetworkErrorKind.TIMEOUT,
            NetworkErrors.classify(java.net.SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `el corte por callTimeout de OkHttp tambien es un timeout`() {
        assertEquals(
            NetworkErrorKind.TIMEOUT,
            NetworkErrors.classify(java.io.InterruptedIOException("timeout"))
        )
    }

    @Test
    fun `un nombre que no resuelve es falta de conexion`() {
        assertEquals(
            NetworkErrorKind.OFFLINE,
            NetworkErrors.classify(java.net.UnknownHostException("bookwyrm.social"))
        )
    }

    @Test
    fun `una conexion rechazada es falta de conexion`() {
        assertEquals(
            NetworkErrorKind.OFFLINE,
            NetworkErrors.classify(java.net.ConnectException("Connection refused"))
        )
    }

    @Test
    fun `el HTML donde se esperaba JSON es una respuesta inesperada`() {
        assertEquals(
            NetworkErrorKind.UNEXPECTED_RESPONSE,
            NetworkErrors.classify(com.google.gson.JsonSyntaxException("Expected BEGIN_OBJECT"))
        )
    }

    @Test
    fun `un fallo cualquiera se queda sin clasificar`() {
        assertEquals(
            NetworkErrorKind.OTHER,
            NetworkErrors.classify(IllegalStateException("algo raro"))
        )
    }

    @Test
    fun `un timeout escondido en el texto se reconoce igual`() {
        assertEquals(
            NetworkErrorKind.TIMEOUT,
            NetworkErrors.classify(java.io.IOException("Canceled due to timeout"))
        )
    }

    // --- códigos HTTP ---

    @Test
    fun `un 502 es la instancia con problemas`() {
        assertEquals(NetworkErrorKind.SERVER_DOWN, NetworkErrors.classify(502))
        assertEquals(NetworkErrorKind.SERVER_DOWN, NetworkErrors.classify(500))
        assertEquals(NetworkErrorKind.SERVER_DOWN, NetworkErrors.classify(504))
    }

    @Test
    fun `un 403 es la sesion caducada`() {
        assertEquals(NetworkErrorKind.SESSION_EXPIRED, NetworkErrors.classify(403))
        assertEquals(NetworkErrorKind.SESSION_EXPIRED, NetworkErrors.classify(401))
    }

    @Test
    fun `un 404 es que ya no esta`() {
        assertEquals(NetworkErrorKind.NOT_FOUND, NetworkErrors.classify(404))
    }

    @Test
    fun `un 429 pide esperar`() {
        assertEquals(NetworkErrorKind.RATE_LIMITED, NetworkErrors.classify(429))
    }

    @Test
    fun `un codigo raro se queda sin clasificar`() {
        assertEquals(NetworkErrorKind.OTHER, NetworkErrors.classify(418))
    }
}
