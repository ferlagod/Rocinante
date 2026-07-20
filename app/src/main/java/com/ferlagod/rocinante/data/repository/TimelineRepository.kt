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
import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.ActivityPubObject
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.TimelineUiItem
import com.ferlagod.rocinante.utils.HtmlUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Número máximo de usuarios seguidos cuyos outboxes se cargarán. */
private const val MAX_FOLLOWING_TO_LOAD = 200

/**
 * Tipos de actividad ActivityPub que no tienen relevancia en el timeline y deben filtrarse.
 * Incluye actividades de borrado, reversión y actualizaciones de perfil.
 */
private val IGNORED_ACTIVITY_TYPES = setOf("Delete", "Undo", "Update", "Like", "Reject", "Block")


class TimelineRepository(
    private val api: BookWyrmApi,
    private val userRepository: UserRepository
) {
    /**
     * Carga la línea de tiempo combinando:
     * 1. El outbox del usuario actual
     * 2. Los outboxes de hasta [MAX_FOLLOWING_TO_LOAD] usuarios que sigue el usuario
     *
     * Las portadas de los libros se extraen de los `attachment` de cada actividad
     * (sin peticiones adicionales al servidor — carga lazy en la UI).
     *
     * @param outboxUrl URL del outbox del usuario actual.
     * @param instanceUrl URL de la instancia actual de BookWyrm.
     * @param username Nombre del usuario autenticado.
     * @param actorNameHint Nombre opcional para mostrar del usuario.
     * @param actorAvatarHint Enlace opcional a la imagen del avatar.
     * @return Lista de [TimelineUiItem] lista para mostrarse.
     */
    suspend fun loadTimeline(
        inboxUrl: String?,
        outboxUrl: String?,
        instanceUrl: String,
        username: String,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> = withContext(Dispatchers.IO) {

        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val cleanUser = username.removePrefix("@").substringBefore("@").trim()

        // ─── ESTRATEGIA 1: Scraping del feed HTML autenticado (método principal) ───
        // BookWyrm no expone API REST para el timeline. El feed real (home) se construye
        // en el servidor con Redis + Django ORM y solo es accesible como HTML. Este es
        // el mismo feed que el usuario ve en el navegador y contiene TODAS las actividades
        // de los seguidos, incluidos boosts y posts followers-only.
        val htmlFeedItems = try {
            com.ferlagod.rocinante.data.api.BookWyrmScraper.scrapeHomeFeed(
                api = api,
                instanceUrl = instanceUrl,
                maxPages = 2
            )
        } catch (_: Exception) {
            emptyList()
        }

        if (htmlFeedItems.isNotEmpty()) {
            return@withContext htmlFeedItems
        }

        // ─── ESTRATEGIA 2: Fallback a outboxes individuales (método clásico corregido) ───
        // Si el scraping HTML falla (ej: cambio de HTML en BookWyrm, sesión expirada),
        // reconstruimos el timeline a partir de los outboxes de ActivityPub.

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
    /**
     * Carga el timeline de los usuarios que el usuario actual sigue.
     *
     * @param instanceUrl URL del servidor de BookWyrm.
     * @param username Nombre del usuario activo.
     * @return Lista de actividades en formato [TimelineUiItem].
     */
    suspend fun loadFollowedActivities(
        instanceUrl: String,
        username: String
    ): List<TimelineUiItem> = withContext(Dispatchers.IO) {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val cleanUser = username.removePrefix("@").substringBefore("@").trim()
        loadFollowingActivities(baseUrl, cleanUser)
    }
    /**
     * Carga las actividades de un inbox específico usando su URL de ActivityPub.
     *
     * @param inboxUrl Dirección URL del inbox.
     * @param actorNameHint Nombre del actor para asociarlo a los ítems.
     * @param actorAvatarHint Enlace al avatar del actor.
     * @return Lista de elementos del timeline.
     */
    private suspend fun loadInboxActivities(
        inboxUrl: String?,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> {
        if (inboxUrl.isNullOrBlank()) return emptyList()

        val paginatedUrl = if (inboxUrl.contains("?")) {
            "$inboxUrl&page=1"
        } else {
            "$inboxUrl?page=1"
        }

        return try {
            val inbox = api.getInboxData(paginatedUrl)
            mapActivitiesToItems(
                inbox.orderedItems.orEmpty(),
                actorNameHint = actorNameHint,
                actorAvatarHint = actorAvatarHint
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
    /**
     * Carga las actividades de un outbox específico usando su URL de ActivityPub.
     *
     * @param outboxUrl Dirección URL del outbox.
     * @param actorNameHint Nombre del actor para asociarlo a los ítems.
     * @param actorAvatarHint Enlace al avatar del actor.
     * @return Lista de elementos del timeline.
     */
    suspend fun loadOutboxActivities(
        outboxUrl: String?,
        actorNameHint: String?,
        actorAvatarHint: String?,
        maxPages: Int = 2
    ): List<TimelineUiItem> {
        if (outboxUrl.isNullOrBlank()) return emptyList()

        val allItems = mutableListOf<TimelineUiItem>()

        for (page in 1..maxPages) {
            val paginatedUrl = if (outboxUrl.contains("?")) {
                "$outboxUrl&page=$page"
            } else {
                "$outboxUrl?page=$page"
            }

            try {
                val outbox = api.getOutboxData(paginatedUrl)
                val items = outbox.orderedItems.orEmpty()
                if (items.isEmpty()) break

                val mapped = mapActivitiesToItems(
                    items,
                    actorNameHint = actorNameHint,
                    actorAvatarHint = actorAvatarHint
                )
                allItems.addAll(mapped)

                // Si la página tiene pocos items, probablemente es la última
                if (items.size < 10) break
            } catch (_: Exception) {
                break
            }
        }

        return allItems
    }
    /**
     * Obtiene la lista de usuarios seguidos y carga el outbox de cada uno
     * (limitado a [MAX_FOLLOWING_TO_LOAD]).
     *
     * @param baseUrl URL base de la instancia.
     * @param cleanUser Nombre de usuario limpio.
     * @return Lista de actividades asociadas a los usuarios seguidos.
     */
    private suspend fun loadFollowingActivities(
        baseUrl: String,
        cleanUser: String
    ): List<TimelineUiItem> = coroutineScope {
        // Paginar la colección de seguidos para cargar TODOS los usuarios,
        // no solo la primera página. BookWyrm pagina con ~10-12 items por página.
        val allFollowingUrls = mutableListOf<String>()
        var currentPage = 1
        val maxFollowingPages = 10 // Límite de seguridad para no hacer loops infinitos

        while (currentPage <= maxFollowingPages) {
            val followingUrl = "${baseUrl}user/$cleanUser/following.json?page=$currentPage"
            try {
                val raw = api.getRawJson(followingUrl).string()

                @Suppress("DEPRECATION")
                val root = JsonParser().parse(raw).asJsonObject

                val items: JsonArray = root.getAsJsonArray("orderedItems") ?: JsonArray()
                if (items.size() == 0) break

                val pageUrls = items.mapNotNull { element ->
                    when {
                        element.isJsonPrimitive -> element.asString
                        element.isJsonObject -> (element as JsonObject).get("id")?.asString
                        else -> null
                    }
                }
                allFollowingUrls.addAll(pageUrls)

                // Si ya tenemos suficientes, parar
                if (allFollowingUrls.size >= MAX_FOLLOWING_TO_LOAD) break
                // Si la página tiene pocos items, es la última
                if (items.size() < 10) break

                currentPage++
            } catch (_: Exception) {
                break
            }
        }

        val followingActorUrls = allFollowingUrls.take(MAX_FOLLOWING_TO_LOAD)

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
     *
     * @param actorUrl URL única del perfil del usuario.
     * @return Lista de actividades del actor.
     */
    private suspend fun loadFollowedActorActivities(actorUrl: String): List<TimelineUiItem> {
        // Timeout global de 10s para descargar el perfil Y el outbox de un seguido
        return withTimeoutOrNull(15_000L) {
            try {
                // No usamos ensureJsonUrl porque rompería URLs de Mastodon/Pixelfed.
                // Retrofit ya envía "Accept: application/activity+json", lo cual es el estándar.
                val profileUrl = actorUrl
                
                // Get profile from cache or fetch and put
                val profile = userRepository.profileCache.getOrPut(profileUrl) {
                    val rawJson = api.getRawJson(profileUrl).string()
                    if (!rawJson.trimStart().startsWith("{")) throw IllegalArgumentException("Not a JSON object")
                    Gson().fromJson(rawJson, BookWyrmProfile::class.java)
                }

                val actorName = profile.name?.takeIf { it.isNotBlank() }
                    ?: profile.preferredUsername
                    ?: profile.id?.substringAfterLast("/") ?: ""
                val actorAvatar = profile.icon?.url

                loadOutboxActivities(profile.outbox, actorNameHint = actorName, actorAvatarHint = actorAvatar, maxPages = 2)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }
    /**
     * Convierte una lista de [ActivityPubActivity] en [TimelineUiItem].
     * Extrae la portada del libro desde los `attachment` si están disponibles
     * (sin petición de red adicional).
     *
     * @param activities Lista de actividades crudas recibidas.
     * @param actorNameHint Nombre a usar como fallback para el autor.
     * @param actorAvatarHint Enlace al avatar a usar como fallback.
     * @return Lista convertida de items del timeline.
     */
    private suspend fun mapActivitiesToItems(
        activities: List<ActivityPubActivity>,
        actorNameHint: String?,
        actorAvatarHint: String?
    ): List<TimelineUiItem> = coroutineScope {
        // Filtrar actividades que no son relevantes para el timeline de actividad literaria
        val relevantActivities = activities.filter { it.type !in IGNORED_ACTIVITY_TYPES }

        val deferredItems = relevantActivities.map { activity ->
            async {
                var currentObjectData = activity.objectData

                // Si el object es un String (URL), como en los Announce, lo descargamos
                if (currentObjectData == null && activity.rawObjectData?.isJsonPrimitive == true) {
                    val objectUrl = activity.rawObjectData.asString
                    try {
                        // Se reduce drásticamente el timeout de 5000 a 2000ms para no penalizar la fluidez
                        // en caso de que un servidor remoto sea lento o no responda.
                        // Usamos objectUrl crudo ya que Accept header maneja ActivityPub en Mastodon
                        val fetchedJson = withTimeoutOrNull(4000L) {
                            api.getRawJson(objectUrl).string()
                        }
                        if (fetchedJson != null) {
                            currentObjectData = Gson().fromJson(fetchedJson, ActivityPubObject::class.java)
                        }
                    } catch (_: Exception) {
                        // Ignorar silenciosamente si no se puede descargar el objeto referenciado
                    }
                }

                // Extraer el tipo real del objeto cuando la actividad es un wrapper "Create".
                // Según la spec ActivityPub + BookWyrm, Create envuelve el objeto real
                // (Review, Comment, Quotation, Note). El tipo del objeto es más informativo.
                val resolvedType = when (activity.type) {
                    "Create" -> currentObjectData?.type ?: activity.type ?: "Actividad"
                    else -> activity.type ?: "Actividad"
                }

                // Para actividades Add (añadir libro a estantería), construir contenido descriptivo
                val isAddActivity = activity.type == "Add"
                val bookTitle = currentObjectData?.name ?: currentObjectData?.id?.substringAfterLast("/")

                val rawContent = when {
                    isAddActivity && !bookTitle.isNullOrBlank() ->
                        "Añadió \"$bookTitle\" a su estantería de lectura"
                    isAddActivity ->
                        "Añadió un libro a su estantería de lectura"
                    else ->
                        currentObjectData?.content
                            ?: currentObjectData?.name
                            ?: activity.content
                            ?: activity.name
                            ?: "Sin contenido"
                }

                // Portada: preferimos el primer attachment de tipo imagen o directamente el campo cover (si es un libro)
                val bookCoverUrl = currentObjectData?.attachment
                    ?.firstOrNull { it.mediaType?.startsWith("image/") == true || it.url?.let { u ->
                        u.endsWith(".jpg") || u.endsWith(".png") || u.endsWith(".webp") || u.endsWith(".jpeg")
                    } == true }
                    ?.url ?: currentObjectData?.cover?.url

                val isBook = currentObjectData?.type in listOf("Edition", "Work", "Book")
                val bookUrl = currentObjectData?.inReplyToBook ?: activity.inReplyToBook ?: if (isBook) currentObjectData?.id else null

                var resolvedActorName = actorNameHint ?: ""
                var resolvedActorAvatar = actorAvatarHint

                val actorUrl = activity.actor
                if (!actorUrl.isNullOrBlank()) {
                    try {
                        val profile = userRepository.profileCache[actorUrl] ?: run {
                            val fetchedJson = withTimeoutOrNull(2500L) {
                                api.getRawJson(actorUrl).string()
                            }
                            if (fetchedJson != null && fetchedJson.trimStart().startsWith("{")) {
                                val p = Gson().fromJson(fetchedJson, BookWyrmProfile::class.java)
                                userRepository.profileCache[actorUrl] = p
                                p
                            } else null
                        }
                        if (profile != null) {
                            resolvedActorName = profile.name?.takeIf { it.isNotBlank() }
                                ?: profile.preferredUsername
                                ?: actorUrl.substringAfterLast("/")
                            resolvedActorAvatar = profile.icon?.url
                        }
                    } catch (_: Exception) {}
                }

                TimelineUiItem(
                    id = activity.id.orEmpty(),
                    type = resolvedType,
                    published = activity.published ?: "",
                    content = HtmlUtils.stripHtml(rawContent).ifBlank { if (isAddActivity) "Añadió un libro a su estantería" else "Sin contenido" },
                    bookCoverUrl = bookCoverUrl,
                    bookUrl = bookUrl,
                    actorName = resolvedActorName.ifBlank { "Usuario" },
                    actorAvatarUrl = resolvedActorAvatar,
                    objectId = currentObjectData?.id ?: activity.id.orEmpty()
                )
            }
        }
        deferredItems.map { it.await() }
    }
}
