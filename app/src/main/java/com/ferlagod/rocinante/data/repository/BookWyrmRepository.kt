/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 Fernando Lago (ferlagod)
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU Affero General Public License (AGPLv3).
 * * AVISO DE DOBLE LICENCIA: Para uso comercial o propietario sin 
 * las obligaciones de liberación de código de la AGPLv3, 
 * es necesario adquirir una licencia comercial previa.
 */
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.utils.HtmlUtils
import com.ferlagod.rocinante.data.api.ActivityPubActivity
import com.ferlagod.rocinante.data.api.ActivityPubObject
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmProfile
import com.ferlagod.rocinante.data.model.TimelineUiItem
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Número máximo de usuarios seguidos cuyos outboxes se cargarán. */
private const val MAX_FOLLOWING_TO_LOAD = 15

class BookWyrmRepository(
    private val api: BookWyrmApi
) {
    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, BookWyrmProfile>()
    suspend fun loadProfile(username: String): BookWyrmProfile {
        val cleanUsername = username.removePrefix("@").trim()

        // 1. Descarga del perfil base
        val profile = api.getUserProfile(cleanUsername)

        // 2. Corrutinas para descargar contadores en paralelo asegurando el formato JSON
        coroutineScope {
            val followersDeferred = profile.followers?.let { url ->
                async {
                    try {
                        val jsonUrl = if (url.endsWith(".json")) url else "$url.json"
                        api.getCollectionData(jsonUrl).totalItems ?: 0
                    } catch (_: Exception) { 0 }
                }
            }

            val followingDeferred = profile.following?.let { url ->
                async {
                    try {
                        val jsonUrl = if (url.endsWith(".json")) url else "$url.json"
                        api.getCollectionData(jsonUrl).totalItems ?: 0
                    } catch (_: Exception) { 0 }
                }
            }

            // 3. Asignación de resultados reales
            profile.followersCountLocal = followersDeferred?.await() ?: 0
            profile.followingCountLocal = followingDeferred?.await() ?: 0
        }

        return profile
    }

    /**
     * Carga la línea de tiempo combinando:
     * 1. El outbox del usuario actual
     * 2. Los outboxes de hasta [MAX_FOLLOWING_TO_LOAD] usuarios que sigue el usuario
     *
     * Las portadas de los libros se extraen de los `attachment` de cada actividad
     * (sin peticiones adicionales al servidor — carga lazy en la UI).
     */
    suspend fun loadTimeline(
        outboxUrl: String?,
        instanceUrl: String,
        username: String,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> = coroutineScope {

        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val cleanUser = username.removePrefix("@").trim()

        // --- Outbox propio ---
        val ownActivitiesDeferred = async {
            loadOutboxActivities(outboxUrl, actorNameHint = actorNameHint, actorAvatarHint = actorAvatarHint)
        }

        // --- Outboxes de seguidos (hasta MAX_FOLLOWING_TO_LOAD) ---
        val followingActivitiesDeferred = async {
            loadFollowingActivities(baseUrl, cleanUser)
        }

        val allActivities = (ownActivitiesDeferred.await() + followingActivitiesDeferred.await())
            .sortedByDescending { it.published } // más recientes primero

        allActivities
    }

    suspend fun loadFollowedActivities(
        instanceUrl: String,
        username: String
    ): List<TimelineUiItem> {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val cleanUser = username.removePrefix("@").trim()
        return loadFollowingActivities(baseUrl, cleanUser)
    }

    // ---------------------------------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------------------------------

    /** Carga los ítems del outbox de un actor dado su URL. */
    suspend fun loadOutboxActivities(
        outboxUrl: String?,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> {
        if (outboxUrl.isNullOrBlank()) return emptyList()

        val paginatedUrl = if (outboxUrl.contains("?")) {
            "$outboxUrl&page=1"
        } else {
            "$outboxUrl?page=1"
        }

        return try {
            val outbox = api.getOutboxData(paginatedUrl)
            mapActivitiesToItems(
                outbox.orderedItems.orEmpty(),
                actorNameHint = actorNameHint,
                actorAvatarHint = actorAvatarHint
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Obtiene la lista de usuarios seguidos y carga el outbox de cada uno
     * (limitado a [MAX_FOLLOWING_TO_LOAD]).
     */
    private suspend fun loadFollowingActivities(
        baseUrl: String,
        cleanUser: String
    ): List<TimelineUiItem> = coroutineScope {
        val followingUrl = "${baseUrl}user/$cleanUser/following.json?page=1"

        val followingActorUrls: List<String> = try {
            // Se usa el mismo parseo robusto que en FollowListViewModel para soportar items que sean strings u objetos
            val raw = api.getRawJson(followingUrl).string()
            
            @Suppress("DEPRECATION")
            val root = JsonParser().parse(raw).asJsonObject
            
            val items: JsonArray = root.getAsJsonArray("orderedItems") ?: JsonArray()

            items.mapNotNull { element ->
                when {
                    element.isJsonPrimitive -> element.asString
                    element.isJsonObject -> (element as JsonObject).get("id")?.asString
                    else -> null
                }
            }.take(MAX_FOLLOWING_TO_LOAD)
        } catch (_: Exception) {
            emptyList()
        }

        // Cargar cada perfil de seguido en paralelo para obtener su outbox y datos de actor
        val deferreds = followingActorUrls.map { actorUrl ->
            async {
                loadFollowedActorActivities(actorUrl)
            }
        }

        deferreds.flatMap { it.await() }
    }

    /**
     * Dado la URL de un actor (seguido), descarga su perfil JSON y luego su outbox.
     * Los datos del actor (nombre, avatar) se propagan a cada TimelineUiItem.
     * Usa getRawJson para soportar actores federados de otras instancias y timeouts
     * para no bloquear todo el timeline si un servidor remoto es lento.
     */
    private suspend fun loadFollowedActorActivities(actorUrl: String): List<TimelineUiItem> {
        // Timeout global de 10s para descargar el perfil Y el outbox de un seguido
        return withTimeoutOrNull(10_000L) {
            try {
                val profileUrl = BookWyrmUtils.ensureJsonUrl(actorUrl)
                
                // Get profile from cache or fetch and put
                val profile = profileCache.getOrPut(profileUrl) {
                    val rawJson = api.getRawJson(profileUrl).string()
                    if (!rawJson.trimStart().startsWith("{")) throw IllegalArgumentException("Not a JSON object")
                    Gson().fromJson(rawJson, BookWyrmProfile::class.java)
                }

                val actorName = profile.name?.takeIf { it.isNotBlank() }
                    ?: profile.preferredUsername
                    ?: profile.id?.substringAfterLast("/") ?: ""
                val actorAvatar = profile.icon?.url

                loadOutboxActivities(profile.outbox, actorNameHint = actorName, actorAvatarHint = actorAvatar)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    /**
     * Convierte una lista de [ActivityPubActivity] en [TimelineUiItem].
     * Extrae la portada del libro desde los `attachment` si están disponibles
     * (sin petición de red adicional).
     */
    private suspend fun mapActivitiesToItems(
        activities: List<ActivityPubActivity>,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> = coroutineScope {
        val deferredItems = activities.map { activity ->
            async {
                var currentObjectData = activity.objectData

                // Si el object es un String (URL), como en los Announce, lo descargamos
                if (currentObjectData == null && activity.rawObjectData?.isJsonPrimitive == true) {
                    val objectUrl = activity.rawObjectData.asString
                    try {
                        val fetchedJson = withTimeoutOrNull(5000L) {
                            api.getRawJson(BookWyrmUtils.ensureJsonUrl(objectUrl)).string()
                        }
                        if (fetchedJson != null) {
                            currentObjectData = Gson().fromJson(fetchedJson, ActivityPubObject::class.java)
                        }
                    } catch (_: Exception) {
                        // Ignorar silenciosamente si no se puede descargar el objeto referenciado
                    }
                }

                val rawContent = currentObjectData?.content
                    ?: currentObjectData?.name
                    ?: activity.content
                    ?: activity.name
                    ?: "Sin contenido"

                // Portada: preferimos el primer attachment de tipo imagen o directamente el campo cover (si es un libro)
                val bookCoverUrl = currentObjectData?.attachment
                    ?.firstOrNull { it.mediaType?.startsWith("image/") == true || it.url?.let { u ->
                        u.endsWith(".jpg") || u.endsWith(".png") || u.endsWith(".webp") || u.endsWith(".jpeg")
                    } == true }
                    ?.url ?: currentObjectData?.cover?.url

                val isBook = currentObjectData?.type in listOf("Edition", "Work", "Book")
                val bookUrl = currentObjectData?.inReplyToBook ?: if (isBook) currentObjectData?.id else null

                TimelineUiItem(
                    id = activity.id.orEmpty(),
                    type = activity.type ?: "Actividad",
                    published = activity.published ?: "",
                    content = HtmlUtils.stripHtml(rawContent).ifBlank { "Sin contenido" },
                    bookCoverUrl = bookCoverUrl,
                    bookUrl = bookUrl,
                    actorName = actorNameHint ?: "",
                    actorAvatarUrl = actorAvatarHint,
                    objectId = currentObjectData?.id ?: activity.id.orEmpty()
                )
            }
        }
        deferredItems.map { it.await() }
    }

    suspend fun resolveLocalStatusId(instanceUrl: String, statusUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
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
                    e.printStackTrace()
                }
                
                null
            } catch (e: Exception) {
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

    suspend fun getUserId(profileUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val shelfUrl = "$profileUrl/books/to-read"
                val response = api.getRawHtmlResponse(shelfUrl)
                if (response.isSuccessful) {
                    val html = response.body()?.string() ?: ""
                    val userMatch = "name=[\"']user[\"'][^>]*?value=[\"'](\\d+)[\"']|value=[\"'](\\d+)[\"'][^>]*?name=[\"']user[\"']".toRegex(RegexOption.IGNORE_CASE).find(html)
                    userMatch?.let { it.groups[1]?.value ?: it.groups[2]?.value }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun favoriteStatus(statusId: String): Boolean {
        return try {
            val response = api.favoriteStatus(statusId)
            response.isSuccessful || response.code() in 200..299 || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unfavoriteStatus(statusId: String): Boolean {
        return try {
            val response = api.unfavoriteStatus(statusId)
            response.isSuccessful || response.code() in 200..299 || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }

    suspend fun replyStatus(userId: String, content: String, replyParent: String, csrfToken: String): Boolean {
        return try {
            val response = api.replyStatus(userId = userId, content = content, replyParent = replyParent, csrfToken = csrfToken)
            response.isSuccessful || response.code() == 302
        } catch (_: Exception) {
            false
        }
    }
}