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

import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.TimelineUiItem

/**
 * A dónde hay que saltar dentro de «Mis libros»: a un libro concreto, porque se ha pulsado un
 * resultado de la búsqueda en las estanterías propias, o a los libros de un autor, porque se ha
 * pulsado su barra en el perfil.
 *
 * @property shelfSlug Estantería que hay que abrir ("to-read", "reading", "read"...).
 * @property bookId Identificador (URL) del libro al que desplazarse y que se resalta, o null.
 * @property authorName Autor cuyos libros hay que enseñar, tal y como lo escribe la instancia,
 *   o null. Va el nombre visible y no una clave porque es lo que tienen tanto el perfil como la
 *   estantería, y cada una lo normaliza igual por su cuenta.
 */
data class ShelfTarget(
    val shelfSlug: String,
    val bookId: String? = null,
    val authorName: String? = null
)

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
 * @property shelfTarget Libro pendiente de abrir en su estantería, o null si no hay ninguno.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val profile: BookWyrmProfile? = null,
    val timeline: List<TimelineUiItem> = emptyList(),
    val visibleTimeline: List<TimelineUiItem> = emptyList(),
    val likedStatusIds: Set<String> = emptySet(),
    val boostedStatusIds: Set<String> = emptySet(),
    val userId: String? = null,
    val currentPage: Int = 1,
    val selectedTab: Int = 0,
    val shelfTarget: ShelfTarget? = null
)