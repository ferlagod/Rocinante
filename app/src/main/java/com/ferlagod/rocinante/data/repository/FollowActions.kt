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
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.utils.BookWyrmUtils
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Seguir, dejar de seguir y atender solicitudes de seguimiento, en un solo sitio.
 *
 * Antes cada pantalla (la lista de seguidores y seguidos, la ficha de usuario) tenía su propia
 * cadena de reintentos, con diferencias entre ellas; una llegó a probar con el nombre a secas
 * de un usuario remoto, que en la instancia es otra persona.
 *
 * BookWyrm identifica al usuario en el campo `user` por su nombre en la instancia: «nombre»
 * si es local y «nombre@servidor» si es remoto. Como no siempre se tiene ese nombre exacto
 * (llega un handle, una URL de actor local o remota…), se prueban varias formas, de la más
 * probable a la menos, y se para en la primera que el servidor acepta.
 */
object FollowActions {

    /**
     * @property success Si el servidor aceptó la acción.
     * @property code Código HTTP de la última respuesta, para enseñarlo si falló.
     */
    data class Result(val success: Boolean, val code: Int?)

    /** Sigue a un usuario. Con uno remoto que la instancia aún no conoce, lo descubre antes. */
    suspend fun follow(api: BookWyrmApi, instanceBaseUrl: String, handle: String, actorUrl: String): Result =
        followOrUnfollow(api, instanceBaseUrl, handle, actorUrl, follow = true)

    /** Deja de seguir a un usuario (o retira la solicitud pendiente). */
    suspend fun unfollow(api: BookWyrmApi, instanceBaseUrl: String, handle: String, actorUrl: String): Result =
        followOrUnfollow(api, instanceBaseUrl, handle, actorUrl, follow = false)

    /** Acepta la solicitud de seguimiento de un usuario. */
    suspend fun acceptRequest(api: BookWyrmApi, formUserId: String?, handle: String, actorUrl: String): Result =
        tryInOrder(requestCandidates(formUserId, handle, actorUrl)) { api.acceptFollowRequest(it) }

    /** Rechaza la solicitud de seguimiento de un usuario. */
    suspend fun rejectRequest(api: BookWyrmApi, formUserId: String?, handle: String, actorUrl: String): Result =
        tryInOrder(requestCandidates(formUserId, handle, actorUrl)) { api.deleteFollowRequest(it) }

    /**
     * Formas de nombrar al usuario para seguirle o dejar de seguirle, en el orden en que se
     * prueban. Si el usuario es de otro servidor nunca se incluye su nombre sin servidor: en
     * la instancia ese nombre es el de una cuenta local distinta.
     */
    internal fun followCandidates(instanceBaseUrl: String, handle: String, actorUrl: String): List<String> {
        val cleanHandle = handle.trim().removePrefix("@").trim()
        val slug = actorUrl.trim().removeSuffix("/").removeSuffix(".json")
            .substringAfterLast("/user/").substringAfterLast("/users/").substringAfterLast("/")
            .removePrefix("@").trim()
        val actorHost = hostOf(actorUrl)
        val myHost = hostOf(instanceBaseUrl)
        val isRemoteFromUrl = actorHost != null && myHost != null && !actorHost.equals(myHost, ignoreCase = true)
        val handleIsRemote = cleanHandle.contains("@")

        val candidates = mutableListOf<String>()
        if (cleanHandle.isNotBlank()) candidates += cleanHandle
        if (isRemoteFromUrl && slug.isNotBlank() && !slug.startsWith("http")) {
            candidates += if (slug.contains("@")) slug else "$slug@$actorHost"
        }
        if (!isRemoteFromUrl && !handleIsRemote && slug.isNotBlank() && !slug.startsWith("http")) {
            candidates += slug
        }
        if (cleanHandle.isNotBlank()) candidates += "@$cleanHandle"
        if (actorUrl.isNotBlank()) candidates += actorUrl.trim()
        return candidates.distinct()
    }

    private fun requestCandidates(formUserId: String?, handle: String, actorUrl: String): List<String> =
        listOfNotNull(
            formUserId?.trim()?.takeIf { it.isNotBlank() },
            handle.trim().removePrefix("@").trim().takeIf { it.isNotBlank() },
            actorUrl.trim().takeIf { it.isNotBlank() }
        ).distinct()

    private suspend fun followOrUnfollow(
        api: BookWyrmApi,
        instanceBaseUrl: String,
        handle: String,
        actorUrl: String,
        follow: Boolean
    ): Result {
        val candidates = followCandidates(instanceBaseUrl, handle, actorUrl)
        var lastCode: Int? = null
        for (candidate in candidates) {
            val response = attempt { if (follow) api.followUser(candidate) else api.unfollowUser(candidate) }
            if (response != null && BookWyrmUtils.isWriteSuccess(response)) {
                BookWyrmUtils.closeQuietly(response)
                return Result(true, response.code())
            }
            lastCode = response?.code() ?: lastCode
            BookWyrmUtils.closeQuietly(response)

            // Un remoto que la instancia aún no conoce da 404: buscarlo por su nombre completo
            // hace que lo importe (WebFinger), y entonces ya se le puede seguir.
            if (follow && candidate.contains("@") && !candidate.startsWith("@") && !candidate.startsWith("http")) {
                discoverRemoteUser(api, instanceBaseUrl, candidate)
                val retry = attempt { api.followUser(candidate) }
                if (retry != null && BookWyrmUtils.isWriteSuccess(retry)) {
                    BookWyrmUtils.closeQuietly(retry)
                    return Result(true, retry.code())
                }
                lastCode = retry?.code() ?: lastCode
                BookWyrmUtils.closeQuietly(retry)
            }
        }
        return Result(false, lastCode)
    }

    private suspend fun tryInOrder(
        candidates: List<String>,
        call: suspend (String) -> Response<ResponseBody>
    ): Result {
        var lastCode: Int? = null
        for (candidate in candidates) {
            val response = attempt { call(candidate) }
            if (response != null && BookWyrmUtils.isWriteSuccess(response)) {
                BookWyrmUtils.closeQuietly(response)
                return Result(true, response.code())
            }
            lastCode = response?.code() ?: lastCode
            BookWyrmUtils.closeQuietly(response)
        }
        return Result(false, lastCode)
    }

    private suspend fun discoverRemoteUser(api: BookWyrmApi, instanceBaseUrl: String, fullHandle: String) {
        val query = java.net.URLEncoder.encode(fullHandle, "UTF-8")
        val base = instanceBaseUrl.trim().trimEnd('/')
        val searchUrl = if (base.isNotBlank()) "$base/search?q=$query&type=user" else "search?q=$query&type=user"
        val response = attempt { api.getRawHtmlResponse(searchUrl) }
        BookWyrmUtils.closeQuietly(response)
    }

    private suspend fun <T> attempt(call: suspend () -> T): T? = try {
        call()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

    private fun hostOf(url: String): String? =
        try { java.net.URI(url.trim()).host?.lowercase() } catch (_: Exception) { null }
}
