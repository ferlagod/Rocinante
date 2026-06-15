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
import com.ferlagod.rocinante.data.api.BookWyrmProfile
import com.ferlagod.rocinante.data.model.FollowUserItem
import com.ferlagod.rocinante.data.local.FollowListCache
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ferlagod.rocinante.utils.BookWyrmUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
private const val MAX_PROFILES_TO_FETCH = 50

/** Timeout por petición de perfil individual (ms) */
private const val PROFILE_FETCH_TIMEOUT_MS = 10_000L

/**
 * ViewModel que gestiona la lógica para cargar, mostrar y alterar las listas
 * de seguidores y usuarios seguidos. Soporta paginación, caché local
 * y la acción de seguir o dejar de seguir a los usuarios listados.
 *
 * @property api Interfaz de red autenticada para interactuar con BookWyrm.
 * @property cache Mecanismo de persistencia local de la lista.
 */
class FollowListViewModel(
    private val api: BookWyrmApi,
    private val cache: FollowListCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowListUiState())
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    /**
     * Carga la lista de seguidores o seguidos del usuario.
     * Estrategia:
     * 1. Obtiene la lista de mis seguidos (IDs) en paralelo con la lista target
     * 2. Para cada actor de la lista target, obtiene el perfil con timeout individual
     * 3. Construye el estado con isFollowedByMe cruzando los dos conjuntos
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
                coroutineScope {
                    // 1. Cargar mis seguidos y la lista target en paralelo
                    val myFollowingDeferred = async {
                        loadAllActorUrls("${baseUrl}user/$username/following.json?page=1")
                    }
                    val targetListDeferred = async {
                        val path = when (direction) {
                            FollowListDirection.FOLLOWERS -> "followers"
                            FollowListDirection.FOLLOWING -> "following"
                        }
                        loadActorUrls("${baseUrl}user/$username/$path.json?page=1")
                    }

                    val myFollowingUrls = myFollowingDeferred.await()
                    val targetActorUrls = targetListDeferred.await()
                        .take(MAX_PROFILES_TO_FETCH)

                    // 2. Cargar perfiles en paralelo con timeout individual
                    val profiles = targetActorUrls.map { actorUrl ->
                        async { fetchProfileWithTimeout(actorUrl) }
                    }.map { it.await() }

                    // 3. Construir items
                    val followingIds = myFollowingUrls.map { normalizeActorUrl(it) }.toSet()
                    val items = profiles.filterNotNull().map { profile ->
                        val actorId = profile.id.orEmpty()
                        FollowUserItem(
                            actorUrl = actorId,
                            name = profile.name?.takeIf { it.isNotBlank() }
                                ?: profile.preferredUsername
                                ?: actorId.substringAfterLast("/"),
                            handle = buildHandle(profile, baseUrl),
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
        _uiState.update { state ->
            state.copy(
                pendingHandles = state.pendingHandles + handle,
                users = state.users.map { user ->
                    if (user.actorUrl == actorUrl) user.copy(isFollowedByMe = follow) else user
                }
            )
        }

        viewModelScope.launch {
            try {
                val cleanHandle = handle.removePrefix("@")
                val response = if (follow) api.followUser(cleanHandle) else api.unfollowUser(cleanHandle)
                
                val isRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true

                // BookWyrm devuelve 200 o 302 en éxito
                if ((!response.isSuccessful && response.code() !in 300..399) || isRedirectToLogin) {
                    revertFollowState(actorUrl, !follow)
                }
            } catch (_: Exception) {
                revertFollowState(actorUrl, !follow)
            } finally {
                _uiState.update { it.copy(pendingHandles = it.pendingHandles - handle) }
            }
        }
    }

    private fun revertFollowState(actorUrl: String, revertedValue: Boolean) {
        _uiState.update { state ->
            state.copy(
                users = state.users.map { user ->
                    if (user.actorUrl == actorUrl) user.copy(isFollowedByMe = revertedValue) else user
                }
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
        return loadAllActorUrls(url, maxPages = 1)
    }

    private suspend fun loadAllActorUrls(initialUrl: String, maxPages: Int = 10): List<String> {
        val allUrls = mutableListOf<String>()
        var currentUrl: String? = initialUrl
        var pagesFetched = 0

        while (currentUrl != null && pagesFetched < maxPages) {
            try {
                val raw = withTimeoutOrNull(15_000L) {
                    api.getRawJson(currentUrl!!).string()
                } ?: break

                @Suppress("DEPRECATION")
                val root = JsonParser().parse(raw).asJsonObject
                val items: JsonArray = root.getAsJsonArray("orderedItems") ?: break

                items.mapNotNull { element ->
                    when {
                        element.isJsonPrimitive -> element.asString  // URL directa
                        element.isJsonObject -> {
                            // Objeto actor completo — extraemos el id
                            (element as JsonObject).get("id")?.asString
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
        return allUrls
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
        return try {
            val jsonUrl = BookWyrmUtils.ensureJsonUrl(actorUrl)
            val raw = api.getRawJson(jsonUrl).string()
            // Verificar que sea JSON antes de parsear (evita parsear HTML de redirects)
            if (!raw.trimStart().startsWith("{")) return null
            gson.fromJson(raw, BookWyrmProfile::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildHandle(profile: BookWyrmProfile, myBaseUrl: String): String {
        val preferredUsername = profile.preferredUsername
            ?: profile.id?.substringAfterLast("/")
            ?: ""

        val actorHost = try {
            java.net.URI(profile.id ?: myBaseUrl).host ?: ""
        } catch (_: Exception) { "" }

        return if (actorHost.isNotEmpty()) "@$preferredUsername@$actorHost"
        else "@$preferredUsername"
    }

    private fun normalizeActorUrl(url: String): String {
        return url.removeSuffix(".json").removeSuffix("/").trim()
    }
}
