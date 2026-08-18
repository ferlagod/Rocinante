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

import android.content.Context
import com.ferlagod.rocinante.R

/**
 * Qué ha ido mal en una petición, en los términos en los que le importa a quien usa la app.
 * Casi siempre el fallo no es suyo ni de la app: la instancia se ha caído, va lenta o la
 * sesión ha caducado, y eso es lo que hay que contarle en lugar de un «timeout» a secas.
 */
enum class NetworkErrorKind {
    /** El teléfono no llega a la red (sin datos, wifi sin salida, DNS que no resuelve). */
    OFFLINE,

    /** La instancia no contesta a tiempo: suele estar saturada. */
    TIMEOUT,

    /** La instancia contesta con un error suyo (5xx): está caída o desbordada. */
    SERVER_DOWN,

    /** La instancia pide esperar antes de seguir pidiendo (429). */
    RATE_LIMITED,

    /** La sesión ya no vale (401/403): hay que volver a entrar. */
    SESSION_EXPIRED,

    /** Lo pedido ya no existe en la instancia (404). */
    NOT_FOUND,

    /**
     * La respuesta no es la esperada: en vez del JSON llega otra cosa, normalmente el HTML
     * de un control tipo Anubis o de un proxy por delante de la instancia.
     */
    UNEXPECTED_RESPONSE,

    /** Cualquier otra cosa; se enseña el detalle técnico porque no sabemos traducirlo. */
    OTHER
}

/**
 * Traduce fallos de red y códigos HTTP a un mensaje que se pueda enseñar tal cual.
 *
 * La clasificación ([classify]) no toca Android, así que se puede probar en la JVM;
 * [message] es lo único que necesita un [Context] para sacar el texto traducido.
 */
object NetworkErrors {

    /** Clasifica la excepción que ha tumbado la petición. */
    fun classify(error: Throwable): NetworkErrorKind = when {
        error is retrofit2.HttpException -> classify(error.code())

        // Sin salida a la red: el nombre no resuelve o no se llega al puerto.
        error is java.net.UnknownHostException -> NetworkErrorKind.OFFLINE
        error is java.net.ConnectException -> NetworkErrorKind.OFFLINE
        error is java.net.NoRouteToHostException -> NetworkErrorKind.OFFLINE
        error is java.net.PortUnreachableException -> NetworkErrorKind.OFFLINE

        // SocketTimeoutException hereda de InterruptedIOException, igual que el corte por
        // callTimeout de OkHttp; los dos significan que la instancia no llegó a contestar.
        error is java.io.InterruptedIOException -> NetworkErrorKind.TIMEOUT

        // La conexión se cae a media respuesta (reset, broken pipe): la red se ha ido.
        error is java.net.SocketException -> NetworkErrorKind.OFFLINE

        // Donde esperábamos JSON llega HTML (un control de acceso, un proxy, un error del
        // servidor renderizado) y el parseo se rompe con un error que no dice nada.
        error is com.google.gson.JsonParseException -> NetworkErrorKind.UNEXPECTED_RESPONSE
        error is java.io.EOFException -> NetworkErrorKind.UNEXPECTED_RESPONSE

        // Último recurso: OkHttp envuelve algunos cortes en excepciones genéricas cuyo
        // único rastro es el texto.
        error.message?.contains("timeout", ignoreCase = true) == true -> NetworkErrorKind.TIMEOUT

        else -> NetworkErrorKind.OTHER
    }

    /** Clasifica una respuesta HTTP que no ha ido bien. */
    fun classify(httpCode: Int): NetworkErrorKind = when {
        httpCode == 401 -> NetworkErrorKind.SESSION_EXPIRED
        httpCode == 404 -> NetworkErrorKind.NOT_FOUND
        httpCode == 429 -> NetworkErrorKind.RATE_LIMITED
        httpCode in 500..599 -> NetworkErrorKind.SERVER_DOWN
        else -> NetworkErrorKind.OTHER
    }

    /**
     * Mensaje para enseñar cuando una petición se ha caído con una excepción.
     * Si no sabemos qué ha pasado, se cae al mensaje de siempre con el detalle técnico.
     */
    fun message(context: Context, error: Throwable): String =
        when (val kind = classify(error)) {
            NetworkErrorKind.OTHER ->
                context.getString(R.string.error_network, error.message ?: "")
            else -> textFor(context, kind, httpCode = (error as? retrofit2.HttpException)?.code())
        }

    /**
     * Mensaje para enseñar cuando la instancia ha contestado con un código de error.
     * Si el código no es de los conocidos, se cae al mensaje de siempre con el número.
     */
    fun message(context: Context, httpCode: Int): String =
        when (val kind = classify(httpCode)) {
            NetworkErrorKind.OTHER ->
                context.getString(R.string.error_server, httpCode.toString())
            else -> textFor(context, kind, httpCode)
        }

    private fun textFor(context: Context, kind: NetworkErrorKind, httpCode: Int?): String =
        when (kind) {
            NetworkErrorKind.OFFLINE -> context.getString(R.string.error_conn_offline)
            NetworkErrorKind.TIMEOUT -> context.getString(R.string.error_conn_timeout)
            NetworkErrorKind.SERVER_DOWN ->
                context.getString(R.string.error_instance_down, (httpCode ?: 500).toString())
            NetworkErrorKind.RATE_LIMITED -> context.getString(R.string.error_rate_limited)
            NetworkErrorKind.SESSION_EXPIRED -> context.getString(R.string.error_session_expired)
            NetworkErrorKind.NOT_FOUND -> context.getString(R.string.error_not_found)
            NetworkErrorKind.UNEXPECTED_RESPONSE ->
                context.getString(R.string.error_unexpected_response)
            NetworkErrorKind.OTHER -> context.getString(R.string.error_network, "")
        }
}
