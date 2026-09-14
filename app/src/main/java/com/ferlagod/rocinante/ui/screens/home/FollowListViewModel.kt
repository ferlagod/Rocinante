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
package com.ferlagod.rocinante.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.local.FollowListCache
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.FollowUserItem
import com.ferlagod.rocinante.data.repository.UserRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Enum que define si la lista a cargar corresponde a seguidores o usuarios seguidos.
 */
enum class FollowListDirection { FOLLOWERS, FOLLOWING }

/**
 * Estado inmutable de la interfaz para la lista de seguimiento.
 *
 * @property isLoading Indica si está cargando por primera vez.
 * @property isRefreshing Indica si se está ejecutando un refresco en segundo plano.
 * @property users Lista de usuarios obtenidos (seguidores o seguidos).
 * @property errorMessage Posible mensaje de error a mostrar.
 * @property myFollowingIds Conjunto de identificadores de actores que el usuario actual sigue.
 * @property pendingHandles Indicadores de carga en curso por cada usuario.
 */
data class FollowListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val users: List<FollowUserItem> = emptyList(),
    val errorMessage: String? = null,
    val myFollowingIds: Set<String> = emptySet(),
    val pendingHandles: Set<String> = emptySet()
)

/** Máximo de perfiles a cargar para mostrar en la lista */
private const val MAX_PROFILES_TO_FETCH = 200

/** Timeout por petición de perfil individual (ms) */
private const val PROFILE_FETCH_TIMEOUT_MS = 3_000L




/**
 * ViewModel que gestiona la lógica para cargar, mostrar y alterar las listas
 * de seguidores y usuarios seguidos. Soporta paginación, caché local
 * y la acción de seguir o dejar de seguir a los usuarios listados.
 *
 * @property api Interfaz de red autenticada para interactuar con BookWyrm.
 * @property cache Mecanismo de persistencia local de la lista.
 * @property userRepository Repositorio de perfiles de usuario con caché concurrente.
 */
@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val api: BookWyrmApi,
    private val cache: FollowListCache,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowListUiState())
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(
            com.ferlagod.rocinante.data.model.ProfileIcon::class.java,
            com.ferlagod.rocinante.data.model.ProfileIconDeserializer()
        )
        .setLenient()
        .create()

    /**
     * Carga la lista de seguidores o seguidos del usuario.
     * Estrategia:
     * 1. Muestra inmediatamente los datos de la caché en memoria o persistida
     * 2. Si no hay datos o forceRefresh es true, realiza la petición en segundo plano
     */
    fun load(baseUrl: String, username: String, direction: FollowListDirection, forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.users.isNotEmpty()) {
            return // Usar caché en memoria (el ViewModel sobrevive en HomeScreen)
        }

        viewModelScope.launch {
            if (!forceRefresh && _uiState.value.users.isEmpty()) {
                val cachedUsers = cache.loadList(direction.name)
                if (!cachedUsers.isNullOrEmpty()) {
                    _uiState.update { it.copy(
                        users = cachedUsers,
                        isLoading = false,
                        isRefreshing = true,
                        myFollowingIds = cachedUsers.filter { user -> user.isFollowedByMe }.map { user -> user.actorUrl }.toSet()
                    ) }
                }
            }

            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            } else {
                if (_uiState.value.users.isEmpty()) {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                }
            }

            try {
                val cleanUser = username.removePrefix("@").substringBefore("@").trim()
                coroutineScope {
                    // 1. Cargar mis seguidos y la lista target en paralelo
                    val myFollowingDeferred = async {
                        loadAllActorUrls("${baseUrl}user/$cleanUser/following.json?page=1", maxPages = 5)
                    }
                    val targetListDeferred = async {
                        val path = when (direction) {
                            FollowListDirection.FOLLOWERS -> "followers"
                            FollowListDirection.FOLLOWING -> "following"
                        }
                        loadActorUrls("${baseUrl}user/$cleanUser/$path.json?page=1")
                    }

                    val myFollowingUrls = myFollowingDeferred.await()
                    val targetActorUrls = targetListDeferred.await()
                        .take(MAX_PROFILES_TO_FETCH)

                    // 2. Cargar perfiles en paralelo con timeout individual (en lotes para evitar saturación)
                    val profiles = mutableListOf<BookWyrmProfile>()
                    targetActorUrls.chunked(20).forEach { chunk ->
                        val chunkProfiles = chunk.map { actorUrl ->
                            async { 
                                resolveProfile(actorUrl, baseUrl)
                            }
                        }.map { it.await() }
                        profiles.addAll(chunkProfiles)
                    }

                    // 3. Construir items
                    val followingIds = myFollowingUrls.map { normalizeActorUrl(it) }.toSet()
                    val items = profiles.map { profile ->
                        val actorId = profile.id.orEmpty()
                        FollowUserItem(
                            actorUrl = actorId,
                            name = profile.name?.takeIf { it.isNotBlank() }
                                ?: profile.preferredUsername
                                ?: actorId.substringAfterLast("/"),
                            handle = buildHandle(profile, baseUrl, actorId),
                            summary = profile.summary,
                            avatarUrl = profile.icon?.url,
                            isFollowedByMe = normalizeActorUrl(actorId) in followingIds
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            users = items,
                            myFollowingIds = followingIds
                        )
                    }

                    cache.saveList(direction.name, items)
                }
            } catch (e: CancellationException) {
                throw e  // nunca swallow CancellationException
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    /**
     * Efectúa la acción de seguir a un usuario de manera optimista y solicita el cambio a la API.
     *
     * @param actorUrl Identificador único del usuario (URL del actor).
     * @param handle Identificador visual/arroba del usuario para mostrar carga en la UI.
     */
    fun follow(actorUrl: String, handle: String) {
        toggleFollow(actorUrl, handle, follow = true)
    }

    /**
     * Efectúa la acción de dejar de seguir a un usuario de manera optimista y solicita el cambio a la API.
     *
     * @param actorUrl Identificador único del usuario (URL del actor).
     * @param handle Identificador visual/arroba del usuario para mostrar carga en la UI.
     */
    fun unfollow(actorUrl: String, handle: String) {
        toggleFollow(actorUrl, handle, follow = false)
    }

    private fun toggleFollow(actorUrl: String, handle: String, follow: Boolean) {
        val updatedUsers = _uiState.value.users.map { user ->
            if (user.actorUrl == actorUrl || user.handle == handle) user.copy(isFollowedByMe = follow) else user
        }
        val updatedFollowing = if (follow) {
            _uiState.value.myFollowingIds + normalizeActorUrl(actorUrl)
        } else {
            _uiState.value.myFollowingIds - normalizeActorUrl(actorUrl)
        }

        _uiState.update { state ->
            state.copy(
                pendingHandles = state.pendingHandles + handle,
                users = updatedUsers,
                myFollowingIds = updatedFollowing
            )
        }

        viewModelScope.launch {
            try {
                val cleanHandle = handle.removePrefix("@")
                var response = if (follow) api.followUser(cleanHandle) else api.unfollowUser(cleanHandle)
                
                val isRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true
                val isSuccess = (response.isSuccessful || (response.code() in 300..399 && !isRedirectToLogin))

                if (!isSuccess && actorUrl.isNotBlank() && actorUrl != cleanHandle) {
                    // Fallback con actorUrl
                    response = if (follow) api.followUser(actorUrl) else api.unfollowUser(actorUrl)
                }

                val finalRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true
                val finalSuccess = (response.isSuccessful || (response.code() in 300..399 && !finalRedirectToLogin))

                if (!finalSuccess) {
                    revertFollowState(actorUrl, handle, !follow)
                } else {
                    cache.saveList(FollowListDirection.FOLLOWING.name, _uiState.value.users)
                    cache.saveList(FollowListDirection.FOLLOWERS.name, _uiState.value.users)
                }
            } catch (_: Exception) {
                revertFollowState(actorUrl, handle, !follow)
            } finally {
                _uiState.update { it.copy(pendingHandles = it.pendingHandles - handle) }
            }
        }
    }

    private fun revertFollowState(actorUrl: String, handle: String, revertedValue: Boolean) {
        _uiState.update { state ->
            val revertedUsers = state.users.map { user ->
                if (user.actorUrl == actorUrl || user.handle == handle) user.copy(isFollowedByMe = revertedValue) else user
            }
            val revertedFollowing = if (revertedValue) {
                state.myFollowingIds + normalizeActorUrl(actorUrl)
            } else {
                state.myFollowingIds - normalizeActorUrl(actorUrl)
            }
            state.copy(
                users = revertedUsers,
                myFollowingIds = revertedFollowing
            )
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Obtiene la lista de URLs de actores de una colección ActivityPub.
     *
     * BookWyrm puede devolver en orderedItems:
     *   - Una lista de strings (URLs de actores) — caso más común
     *   - Una lista de objetos JSON (actores completos) — algunas versiones
     *
     * Esta función maneja ambos casos parseando el JSON raw.
     */
    private suspend fun loadActorUrls(url: String): List<String> {
        return loadAllActorUrls(url, maxPages = 50)
    }

    private suspend fun loadAllActorUrls(initialUrl: String, maxPages: Int = 50): List<String> {
        val allUrls = mutableListOf<String>()
        var currentUrl: String? = initialUrl
        var pagesFetched = 0

        while (currentUrl != null && pagesFetched < maxPages) {
            try {
                val raw = withTimeoutOrNull(15_000L) {
                    api.getRawJson(currentUrl!!).string()
                } ?: break

                @Suppress("DEPRECATION")
                var root = JsonParser().parse(raw).asJsonObject
                
                // Fallback para instancias antiguas (BookWyrm 0.8) que devuelven el perfil del usuario (Person)
                // en lugar de la colección al consultar followers.json o following.json
                if (!root.has("orderedItems") && root.get("type")?.asString == "Person") {
                    val fallbackUrl = if (initialUrl.contains("/following")) {
                        root.get("following")?.asString
                    } else {
                        root.get("followers")?.asString
                    }
                    if (fallbackUrl != null) {
                        val pagedFallbackUrl = if (fallbackUrl.contains("?")) fallbackUrl else "$fallbackUrl?page=1"
                        val fallbackRaw = withTimeoutOrNull(15_000L) {
                            api.getRawJson(pagedFallbackUrl).string()
                        } ?: break
                        @Suppress("DEPRECATION")
                        root = JsonParser().parse(fallbackRaw).asJsonObject
                    }
                }

                val items: JsonArray = root.getAsJsonArray("orderedItems") ?: break

                items.mapNotNull { element ->
                    when {
                        element.isJsonPrimitive -> element.asString  // URL directa
                        element.isJsonObject -> {
                            val obj = element.asJsonObject
                            val id = obj.get("id")?.asString
                            if (id != null) {
                                try {
                                    val profile = gson.fromJson(obj, BookWyrmProfile::class.java)
                                    if (profile != null) {
                                        userRepository.profileCache[id] = profile
                                        userRepository.profileCache[normalizeActorUrl(id)] = profile
                                    }
                                } catch (_: Exception) {}
                            }
                            id
                        }
                        else -> null
                    }
                }.let { allUrls.addAll(it) }

                currentUrl = root.get("next")?.asString
                pagesFetched++
            } catch (_: Exception) {
                break
            }
        }

        if (allUrls.isEmpty()) {
            val htmlUrl = initialUrl.substringBefore("?").removeSuffix(".json")
            try {
                val resp = api.getRawHtmlResponse(htmlUrl)
                if (resp.isSuccessful) {
                    val html = resp.body()?.string().orEmpty()
                    val doc = org.jsoup.Jsoup.parse(html)
                    val base = try {
                        val uri = java.net.URI(initialUrl)
                        "${uri.scheme}://${uri.host}"
                    } catch (_: Exception) { "" }

                    val targetUsername = initialUrl.substringAfter("/user/").substringBefore("/").substringBefore(".").trim()

                    val userBlocks = doc.select(".columns:has(a[href*=/user/]), tr.user-preview, tr:has(a[href*=/user/]), .user-preview, div.media:has(a[href*=/user/]), li:has(a[href*=/user/])")
                    for (block in userBlocks) {
                        // Ignorar la tarjeta superior del perfil propio
                        if (block.selectFirst(".preserve-whitespace") != null || block.selectFirst("a[href*=/followers]") != null) continue

                        val userLink = block.selectFirst("a[href*=/user/]") ?: continue
                        val href = userLink.attr("href").trim()
                        if (href.isNotEmpty()) {
                            val fullUrl = if (href.startsWith("http")) href else if (base.isNotEmpty()) "$base/${href.trimStart('/')}" else href
                            val cleanUserUrl = fullUrl.substringBefore("?").removeSuffix("/")
                            val path = try { java.net.URI(cleanUserUrl).path?.trim('/') } catch (_: Exception) { null }
                            val segments = path?.split('/') ?: emptyList()
                            if (segments.size == 2 && segments[0] == "user" && segments[1] != targetUsername) {
                                allUrls.add(cleanUserUrl)

                                val name = userLink.text().trim()
                                val avatarSrc = block.selectFirst("img.avatar, img")?.attr("src")
                                val avatarUrl = avatarSrc?.takeIf { it.isNotBlank() }?.let {
                                    if (it.startsWith("http")) it else if (base.isNotEmpty()) "$base/${it.trimStart('/')}" else it
                                }

                                val parsedProfile = BookWyrmProfile(
                                    id = cleanUserUrl,
                                    type = "Person",
                                    name = name.ifBlank { cleanUserUrl.substringAfterLast("/") },
                                    summary = null,
                                    outbox = null,
                                    inbox = null,
                                    icon = avatarUrl?.let { com.ferlagod.rocinante.data.model.ProfileIcon(it) },
                                    preferredUsername = cleanUserUrl.substringAfterLast("/"),
                                    followers = null,
                                    following = null
                                )
                                userRepository.profileCache[cleanUserUrl] = parsedProfile
                                userRepository.profileCache[normalizeActorUrl(cleanUserUrl)] = parsedProfile
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return allUrls.distinct()
    }

    /**
     * Resuelve el perfil de un actor consultando primero la caché, luego directamente,
     * posteriormente a través de la instancia local (para usuarios federados) y finalmente
     * mediante raspado de OpenGraph HTML.
     */
    private suspend fun resolveProfile(actorUrl: String, baseUrl: String): BookWyrmProfile {
        // 1. Comprobar caché en memoria
        userRepository.profileCache[actorUrl]?.let { return it }
        userRepository.profileCache[normalizeActorUrl(actorUrl)]?.let { return it }

        // 2. Intento de descarga directa
        var profile = fetchProfileWithTimeout(actorUrl)

        // 3. Fallback: resolver a través de la instancia local para usuarios federados
        if (profile == null) {
            val uri = try { java.net.URI(actorUrl) } catch (_: Exception) { null }
            val host = uri?.host
            val username = actorUrl.removeSuffix("/").substringAfterLast("/")
            val localHost = try { java.net.URI(baseUrl).host } catch (_: Exception) { null }

            if (!host.isNullOrBlank() && !localHost.isNullOrBlank() && !host.equals(localHost, ignoreCase = true) && username.isNotBlank()) {
                val localFederatedUrl = "${baseUrl.trimEnd('/')}/user/$username@$host.json"
                profile = fetchProfileWithTimeout(localFederatedUrl)
            }
        }

        // 4. Fallback: raspado OpenGraph HTML
        if (profile == null) {
            profile = fetchProfileHtmlFollowingRedirects(actorUrl)
        }

        // 5. Fallback final con información básica
        val resolved = profile ?: BookWyrmProfile(
            id = actorUrl,
            type = "Person",
            name = actorUrl.substringAfterLast("/").substringBefore("?").replace("@", ""),
            summary = null,
            outbox = null,
            inbox = null,
            icon = null,
            preferredUsername = actorUrl.substringAfterLast("/").substringBefore("?").replace("@", ""),
            followers = null,
            following = null
        )

        userRepository.profileCache[actorUrl] = resolved
        userRepository.profileCache[normalizeActorUrl(actorUrl)] = resolved
        return resolved
    }

    /**
     * Descarga el perfil de un actor con timeout por petición.
     * Maneja redirects manualmente (el OkHttpClient tiene followRedirects=false).
     */
    private suspend fun fetchProfileWithTimeout(actorUrl: String): BookWyrmProfile? {
        return withTimeoutOrNull(PROFILE_FETCH_TIMEOUT_MS) {
            fetchProfileFollowingRedirects(actorUrl)
        }
    }

    private suspend fun fetchProfileFollowingRedirects(actorUrl: String): BookWyrmProfile? {
        var currentUrl = actorUrl
        var redirects = 0
        while (redirects < 5) {
            try {
                val response = api.getRawJsonResponse(currentUrl)
                if (response.isSuccessful) {
                    val raw = response.body()?.string() ?: return null
                    if (!raw.trimStart().startsWith("{")) return null
                    return gson.fromJson(raw, BookWyrmProfile::class.java)
                } else if (response.code() in 300..399) {
                    val location = response.headers()["Location"] ?: return null
                    currentUrl = if (location.startsWith("http")) location else {
                        java.net.URI(currentUrl).resolve(location).toString()
                    }
                    redirects++
                } else {
                    return null
                }
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private fun buildHandle(profile: BookWyrmProfile, myBaseUrl: String, actorUrl: String = ""): String {
        val preferredUsername = profile.preferredUsername?.takeIf { it.isNotBlank() }
            ?: profile.name?.takeIf { it.isNotBlank() }
            ?: actorUrl.substringAfterLast("/").takeIf { it.isNotBlank() }
            ?: ""

        val myHost = try { java.net.URI(myBaseUrl).host ?: "" } catch (_: Exception) { "" }
        val actorHost = try {
            java.net.URI(profile.id ?: actorUrl).host ?: ""
        } catch (_: Exception) { "" }

        return if (actorHost.isNotEmpty() && !actorHost.equals(myHost, ignoreCase = true)) {
            "@${preferredUsername.removePrefix("@")}@$actorHost"
        } else {
            "@${preferredUsername.removePrefix("@")}"
        }
    }

    private fun normalizeActorUrl(url: String): String {
        return url.removeSuffix(".json").removeSuffix("/").trim()
    }

    /**
     * Intenta raspar (scrape) la información del perfil directamente desde el HTML público (OpenGraph)
     * del usuario. Esto es vital como plan B porque las instancias restrictivas de Mastodon 
     * no permiten descargas del JSON ActivityPub sin firma, pero sí permiten leer el HTML público del perfil.
     */
    private suspend fun fetchProfileHtmlFollowingRedirects(actorUrl: String): BookWyrmProfile? {
        var currentUrl = actorUrl
        var redirects = 0
        while (redirects < 5) {
            try {
                // Se usa la API que ya incluye cabecera Accept: text/html
                val response = api.getRawHtmlResponse(currentUrl)
                if (response.isSuccessful) {
                    val html = response.body()?.string() ?: return null
                    val doc = org.jsoup.Jsoup.parse(html)
                    
                    // Extraer desde las etiquetas OpenGraph y meta estandar
                    val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
                        ?: doc.selectFirst("meta[name=description]")?.attr("content")
                    val image = doc.selectFirst("meta[property=og:image]")?.attr("content")
                    
                    val nameExtracted = title?.substringBefore(" (@") ?: doc.title() 
                        ?: actorUrl.substringAfterLast("/").substringBefore("?")
                        
                    return BookWyrmProfile(
                        id = actorUrl,
                        type = "Person",
                        name = nameExtracted.trim(),
                        summary = description?.trim(),
                        outbox = null,
                        inbox = null,
                        icon = image?.takeIf { it.isNotBlank() }?.let { com.ferlagod.rocinante.data.model.ProfileIcon(it) },
                        preferredUsername = actorUrl.substringAfterLast("/").substringBefore("?").replace("@", ""),
                        followers = null,
                        following = null
                    )
                } else if (response.code() in 300..399) {
                    val location = response.headers()["Location"] ?: return null
                    currentUrl = if (location.startsWith("http")) location else {
                        java.net.URI(currentUrl).resolve(location).toString()
                    }
                    redirects++
                } else {
                    return null
                }
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }
}
