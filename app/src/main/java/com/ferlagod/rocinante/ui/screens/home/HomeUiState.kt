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

/**
 * Estado inmutable que representa la interfaz de usuario de la pantalla principal (Home).
 * Contiene todos los datos necesarios para renderizar la vista y su comportamiento.
 *
 * @property isLoading Indica si la aplicación está realizando la carga inicial de datos.
 * @property isRefreshing Indica si se está ejecutando un refresco manual (pull-to-refresh).
 * @property errorMessage Mensaje de error para mostrar al usuario, o null si no hay error.
 * @property profile Información del perfil del usuario autenticado.
 * @property timeline Lista completa de actividades cargadas en la línea de tiempo.
 * @property selectedTab Índice de la pestaña actualmente seleccionada en la barra de navegación inferior.
 * @property likedStatusIds Conjunto de identificadores de las publicaciones marcadas como favoritas (likes).
 * @property userId Identificador interno único del usuario.
 * @property visibleTimeline Subconjunto de actividades visibles actualmente en la vista (usado para paginación local).
 * @property currentPage Página actual cargada en la vista paginada de la línea de tiempo.
 */
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