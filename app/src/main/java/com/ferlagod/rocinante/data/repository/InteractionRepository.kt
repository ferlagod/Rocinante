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

import com.ferlagod.rocinante.data.model.*

import com.ferlagod.rocinante.utils.HtmlUtils
import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.ActivityPubObject
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.BookSearchResult
import com.ferlagod.rocinante.data.model.TimelineUiItem
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers


class InteractionRepository(
    private val api: BookWyrmApi
) {
    /**
     * Resuelve el identificador local (base de datos interna de la instancia) de una actividad
     * (estado/review/comentario) a partir de su URL pública, interactuando con el HTML
     * de la página si es necesario para mapear el ID remoto con el local.
     *
     * @param instanceUrl Servidor de destino.
     * @param statusUrl URL pública del estado.
     * @return ID numérico local de la actividad, o null si no se puede resolver.
     */
    suspend fun resolveLocalStatusId(instanceUrl: String, statusUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // If it is already a pure numeric ID (which we extract from forms), return it
                if (statusUrl.all { it.isDigit() }) {
                    return@withContext statusUrl
                }
                
                val cleanInstance = instanceUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
                val typeRegex = """/(status|review|comment|quotation|reviewrating|post)/(\d+)""".toRegex()
                
                // If it is already a local status URL (contains our instance url) and has a valid ID
                if (statusUrl.contains(cleanInstance)) {
                    val match = typeRegex.find(statusUrl)
                    if (match != null) {
                        return@withContext match.groupValues[2]
                    }
                }

                // Parse the remote status URL to identify the host and the actor's username
                if (!statusUrl.startsWith("http://") && !statusUrl.startsWith("https://")) {
                    return@withContext null
                }
                val urlObj = java.net.URL(statusUrl)
                val host = urlObj.host
                val path = urlObj.path
                
                val username = when {
                    path.startsWith("/user/") -> {
                        path.substringAfter("/user/").substringBefore("/")
                    }
                    path.contains("/@") -> {
                        path.substringAfter("/@").substringBefore("/")
                    }
                    else -> {
                        val segments = path.split("/").filter { it.isNotEmpty() }
                        if (segments.size >= 2) segments[1] else null
                    }
                }

                if (username != null) {
                    val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
                    val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                    
                    // Construct local profile handle and URL
                    val handle = "$username@$host"
                    val localProfileUrl = "${baseUrl}user/$handle"
                    
                    // Fetch profile page as HTML to find the mapping to the local database ID
                    val response = api.getRawHtmlResponse(localProfileUrl)
                    if (response.isSuccessful) {
                        val html = response.body()?.string() ?: ""
                        val document = org.jsoup.Jsoup.parse(html)
                        
                        val anchors = document.select("a")
                        val cleanStatusUrl = statusUrl.substringBefore("#").trimEnd('/').removeSuffix("/activity")
                        for (anchor in anchors) {
                            val href = anchor.attr("href")
                            val cleanHref = href.substringBefore("#").trimEnd('/')
                            if (cleanHref == cleanStatusUrl) {
                                var current: org.jsoup.nodes.Element? = anchor
                                while (current != null) {
                                    // 1. Check for favorite/unfavorite forms
                                    val favForm = current.selectFirst("form[action*=/favorite/], form[action*=/unfavorite/]")
                                    if (favForm != null) {
                                        val action = favForm.attr("action")
                                        val match = """/(favorite|unfavorite)/(\d+)""".toRegex().find(action)
                                        if (match != null) {
                                            return@withContext match.groupValues[2]
                                        }
                                    }
                                    
                                    // 2. Check for show_comment reply panel ID
                                    val replyPanel = current.selectFirst("[id^=show_comment_]")
                                    if (replyPanel != null) {
                                        val idAttr = replyPanel.attr("id")
                                        val localId = idAttr.removePrefix("show_comment_")
                                        if (localId.all { it.isDigit() }) {
                                            return@withContext localId
                                        }
                                    }
                                    
                                    // 3. Check for delete-status form
                                    val deleteForm = current.selectFirst("form[action*=/delete-status/]")
                                    if (deleteForm != null) {
                                        val action = deleteForm.attr("action")
                                        val match = """/delete-status/(\d+)""".toRegex().find(action)
                                        if (match != null) {
                                            return@withContext match.groupValues[1]
                                        }
                                    }
                                    
                                    current = current.parent()
                                }
                            }
                        }
                    }
                }
                
                // Fallback: extract from original URL
                val match = typeRegex.find(statusUrl)
                if (match != null) {
                    return@withContext match.groupValues[2]
                }
                
                // Final Fallback: search for the status URL on the local instance
                try {
                    val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
                    val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                    val searchUrl = "${baseUrl}search?q=$statusUrl"
                    val searchResponse = api.getRawHtmlResponse(searchUrl)
                    if (searchResponse.isSuccessful || searchResponse.code() in 300..399) {
                        val searchHtml = searchResponse.body()?.string() ?: ""
                        // 1. If it redirected straight to the status
                        val finalUrl = searchResponse.raw().request.url.toString()
                        val redirectMatch = typeRegex.find(finalUrl)
                        if (redirectMatch != null && finalUrl.contains(cleanInstance)) {
                            return@withContext redirectMatch.groupValues[2]
                        }
                        // 2. If it showed search results, parse the first status link
                        val searchDoc = org.jsoup.Jsoup.parse(searchHtml)
                        val statusLink = searchDoc.select("a").firstOrNull { 
                            it.attr("href").contains(cleanInstance) && typeRegex.containsMatchIn(it.attr("href"))
                        }
                        if (statusLink != null) {
                            val hrefMatch = typeRegex.find(statusLink.attr("href"))
                            if (hrefMatch != null) {
                                return@withContext hrefMatch.groupValues[2]
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                }
                
                null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                try {
                    val typeRegex = """/(status|review|comment|quotation|reviewrating|post)/(\d+)""".toRegex()
                    val match = typeRegex.find(statusUrl)
                    if (match != null) {
                        return@withContext match.groupValues[2]
                    }
                } catch (_: Exception) {}
                null
            }
        }
    }
    /**
     * Marca como favorito un estado (le da me gusta) usando su ID local.
     *
     * @param statusId ID de la actividad a favorecer.
     * @return true si la operación se realizó con éxito.
     */
    suspend fun favoriteStatus(statusId: String): Boolean {
        return try {
            val response = api.favoriteStatus(statusId)
            response.isSuccessful || response.code() in 200..299 || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }
    /**
     * Quita de favoritos un estado usando su ID local.
     *
     * @param statusId ID de la actividad.
     * @return true si la operación se realizó con éxito.
     */
    suspend fun unfavoriteStatus(statusId: String): Boolean {
        return try {
            val response = api.unfavoriteStatus(statusId)
            response.isSuccessful || response.code() in 200..299 || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }
    /**
     * Comparte (Boost/Announce) una publicación.
     */
    suspend fun boostStatus(statusId: String): Boolean {
        return try {
            val response = api.boostStatus(statusId)
            response.isSuccessful || response.code() in 200..399
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deshace el compartir (Undo Announce) de una publicación.
     */
    suspend fun unboostStatus(statusId: String): Boolean {
        return try {
            val response = api.unboostStatus(statusId)
            response.isSuccessful || response.code() in 200..399
        } catch (_: Exception) {
            false
        }
    }
    /**
     * Envía una respuesta/comentario a un estado o actividad específica.
     *
     * @param userId ID del usuario que responde.
     * @param content Texto del comentario.
     * @param replyParent ID del estado padre al que se responde.
     * @param csrfToken Token CSRF de seguridad de la instancia.
     * @return true si se publica la respuesta correctamente.
     */
    suspend fun replyStatus(userId: String, content: String, replyParent: String, csrfToken: String): Boolean {
        return try {
            val response = api.replyStatus(userId = userId, content = content, replyParent = replyParent, csrfToken = csrfToken)
            response.isSuccessful || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }
}
