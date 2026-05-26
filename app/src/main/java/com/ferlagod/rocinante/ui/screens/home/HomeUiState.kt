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

import com.ferlagod.rocinante.data.api.BookWyrmProfile
import com.ferlagod.rocinante.data.model.TimelineUiItem

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val profile: BookWyrmProfile? = null,
    val timeline: List<TimelineUiItem> = emptyList(),
    val selectedTab: Int = 0,
    val likedStatusIds: Set<String> = emptySet(),
    val userId: String? = null,
    val visibleTimeline: List<TimelineUiItem> = emptyList(),
    val currentPage: Int = 1
)