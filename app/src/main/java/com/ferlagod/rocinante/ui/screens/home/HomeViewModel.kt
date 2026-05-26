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
package com.ferlagod.rocinante.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.repository.BookWyrmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: BookWyrmRepository,
    private val timelineCache: TimelineCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(instanceUrl: String, username: String, cookie: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.profile != null) {
            return // Usa caché en memoria (ya se cargaron los datos)
        }

        if (instanceUrl.isBlank() || username.isBlank() || cookie.isBlank()) {
            _uiState.value = HomeUiState(
                isLoading = false,
                isRefreshing = false,
                errorMessage = "Faltan datos de sesión."
            )
            return
        }

        viewModelScope.launch {
            if (!forceRefresh && _uiState.value.timeline.isEmpty()) {
                val cachedTimeline = timelineCache.loadTimeline()
                val cachedLikes = timelineCache.loadLikedStatuses()
                if (!cachedTimeline.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        timeline = cachedTimeline,
                        visibleTimeline = cachedTimeline.take(10),
                        currentPage = 1,
                        likedStatusIds = cachedLikes,
                        isLoading = false
                    )
                }
            }

            if (forceRefresh) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = true,
                    errorMessage = null
                )
            } else {
                if (_uiState.value.timeline.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = true,
                        errorMessage = null
                    )
                }
            }

            try {
                val profile = repository.loadProfile(username)
                val cachedLikes = timelineCache.loadLikedStatuses()
                
                // Show cached/empty while loading in background if we forced refresh, or show loading state
                
                // Load merged timeline (own + following) simultaneously
                val mergedTimeline = repository.loadTimeline(
                    outboxUrl = profile.outbox,
                    instanceUrl = instanceUrl,
                    username = username,
                    actorNameHint = profile.name ?: username,
                    actorAvatarHint = profile.icon?.url
                )

                timelineCache.saveTimeline(mergedTimeline)
                val updatedLikes = timelineCache.loadLikedStatuses()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    profile = profile,
                    timeline = mergedTimeline,
                    visibleTimeline = mergedTimeline.take(10),
                    currentPage = 1,
                    likedStatusIds = updatedLikes
                )

                if (_uiState.value.userId == null) {
                    val profileIdUrl = profile.id
                    if (profileIdUrl != null) {
                        launch {
                            val userId = repository.getUserId(profileIdUrl)
                            if (userId != null) {
                                _uiState.value = _uiState.value.copy(userId = userId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = if (_uiState.value.timeline.isEmpty()) "Error cargando datos: ${e.message}" else null
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun updateProfileOptimistically(name: String, summary: String) {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            _uiState.value = _uiState.value.copy(
                profile = currentProfile.copy(
                    name = name,
                    summary = summary
                )
            )
        }
    }

    fun incrementFollowingCount() {
        val currentProfile = _uiState.value.profile
        if (currentProfile != null) {
            _uiState.value = _uiState.value.copy(
                profile = currentProfile.copy(
                    followingCountLocal = currentProfile.followingCountLocal + 1
                )
            )
        }
    }

    fun toggleLike(statusUrl: String, instanceUrl: String) {
        viewModelScope.launch {
            val statusId = repository.resolveLocalStatusId(instanceUrl, statusUrl) ?: return@launch
            val currentlyLiked = _uiState.value.likedStatusIds.contains(statusUrl)
            val newLiked = !currentlyLiked

            val currentSet = _uiState.value.likedStatusIds
            val updatedSet = if (newLiked) currentSet + statusUrl else currentSet - statusUrl
            _uiState.value = _uiState.value.copy(likedStatusIds = updatedSet)
            timelineCache.saveLikedStatuses(updatedSet)

            val success = if (newLiked) {
                repository.favoriteStatus(statusId)
            } else {
                repository.unfavoriteStatus(statusId)
            }

            if (!success) {
                // Revert optimistic update
                val revertedSet = if (newLiked) updatedSet - statusUrl else updatedSet + statusUrl
                _uiState.value = _uiState.value.copy(likedStatusIds = revertedSet)
                timelineCache.saveLikedStatuses(revertedSet)
            }
        }
    }

    fun replyToStatus(
        statusUrl: String,
        instanceUrl: String,
        content: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var userId = _uiState.value.userId
            if (userId == null) {
                val profileUrl = _uiState.value.profile?.id
                if (profileUrl != null) {
                    userId = repository.getUserId(profileUrl)
                    if (userId != null) {
                        _uiState.value = _uiState.value.copy(userId = userId)
                    }
                }
            }

            if (userId == null) {
                onResult(false)
                return@launch
            }

            val parentStatusId = repository.resolveLocalStatusId(instanceUrl, statusUrl)
            if (parentStatusId == null) {
                onResult(false)
                return@launch
            }

            val cookieJar = com.ferlagod.rocinante.data.api.NetworkClient.lastOkHttpClient?.cookieJar as? com.ferlagod.rocinante.data.api.SessionCookieJar
            val csrfToken = cookieJar?.currentCsrfToken() ?: ""

            val success = repository.replyStatus(userId, content, parentStatusId, csrfToken)
            onResult(success)
        }
    }

    fun loadMoreActivities() {
        val currentVisibleSize = _uiState.value.visibleTimeline.size
        val totalSize = _uiState.value.timeline.size
        if (currentVisibleSize < totalSize) {
            val nextPage = _uiState.value.currentPage + 1
            val nextVisible = _uiState.value.timeline.take(nextPage * 10)
            _uiState.value = _uiState.value.copy(
                visibleTimeline = nextVisible,
                currentPage = nextPage
            )
        }
    }
}