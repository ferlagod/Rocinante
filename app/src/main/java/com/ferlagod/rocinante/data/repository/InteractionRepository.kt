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


class InteractionRepository(
    private val api: BookWyrmApi
) {
    /** ¿Es [url] de la instancia del usuario (por host, no por contener el texto)? */
    private fun isLocalUrl(url: String, instanceUrl: String): Boolean {
        val host = runCatching { java.net.URL(url).host }.getOrNull() ?: return false
        val instanceHost = instanceUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")
        return com.ferlagod.rocinante.data.api.NetworkClient.isInstanceHost(host, instanceHost)
    }

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
                
                val typeRegex = STATUS_PATH
                
                // If it is already a local status URL (contains our instance url) and has a valid ID
                if (isLocalUrl(statusUrl, instanceUrl)) {
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
                
                // El número que trae la URL solo vale si el estado es de esta instancia. El de
                // una URL remota es el ID *de allí*: usarlo aquí daba like o respondía a un estado
                // local cualquiera que tuviera ese mismo número.
                if (isLocalUrl(statusUrl, instanceUrl)) {
                    val match = typeRegex.find(statusUrl)
                    if (match != null) {
                        return@withContext match.groupValues[2]
                    }
                }
                
                // Antes se buscaba aquí la dirección del estado en /search, pero BookWyrm solo
                // busca libros, autores, usuarios y listas: nunca encontraba nada y costaba una
                // petición más.
                null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                if (isLocalUrl(statusUrl, instanceUrl)) {
                    val typeRegex = STATUS_PATH
                    val match = typeRegex.find(statusUrl)
                    if (match != null) {
                        return@withContext match.groupValues[2]
                    }
                }
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
            doneOrAlreadyDone(response, alreadyCode = 400)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            doneOrAlreadyDone(response, alreadyCode = 404)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }
    /**
     * Comparte (Boost/Announce) una publicación.
     */
    suspend fun boostStatus(statusId: String): Boolean {
        return try {
            val response = api.boostStatus(statusId)
            com.ferlagod.rocinante.utils.BookWyrmUtils.isWriteSuccess(response).also { com.ferlagod.rocinante.utils.BookWyrmUtils.closeQuietly(response) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    /**
     * Deshace el compartir (Undo Announce) de una publicación.
     */
    suspend fun unboostStatus(statusId: String): Boolean {
        return try {
            val response = api.unboostStatus(statusId)
            doneOrAlreadyDone(response, alreadyCode = 500)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            val response = api.replyStatus(
                userId = userId,
                content = content,
                replyParent = replyParent,
                privacy = PostPrivacy.forPost(api),
                csrfToken = csrfToken
            )
            com.ferlagod.rocinante.utils.BookWyrmUtils.isWriteSuccess(response).also { com.ferlagod.rocinante.utils.BookWyrmUtils.closeQuietly(response) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    /**
     * ¿Ha quedado el estado como se pedía? Lo ha hecho la petición, o ya estaba así.
     *
     * BookWyrm no contesta a «ya estaba hecho» con un éxito: dar me gusta a lo que ya lo tiene es
     * un 400, quitar uno que no hay es un 404, y deshacer una difusión que no existe acaba en un
     * 500 (la vista intenta borrar algo que no encuentra). Pasa cuando lo que enseña la app va por
     * detrás de lo que se hizo desde la web, y tratarlo como fallo deshacía el cambio en pantalla
     * sin que el estado llegara nunca a cuadrar. Ninguno de esos códigos guarda nada, así que no
     * hay nada que repetir: el estado ya es el que se quería.
     */
    private fun doneOrAlreadyDone(response: retrofit2.Response<*>, alreadyCode: Int): Boolean =
        (com.ferlagod.rocinante.utils.BookWyrmUtils.isWriteSuccess(response) || response.code() == alreadyCode)
            .also { com.ferlagod.rocinante.utils.BookWyrmUtils.closeQuietly(response) }

    companion object {
        /**
         * La dirección de un estado de BookWyrm: `/user/<nombre>/<tipo>/<número>`, con los tipos
         * que admite su `urls.py`. Faltaban las notas de lectura y las difusiones, así que a esas
         * no se les sacaba el número; «post» no es ningún tipo y sobraba.
         */
        internal val STATUS_PATH =
            """/(status|review|reviewrating|comment|quotation|boost|generatednote)/(\d+)""".toRegex()
    }
}
