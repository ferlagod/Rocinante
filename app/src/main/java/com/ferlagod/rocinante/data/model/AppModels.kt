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
package com.ferlagod.rocinante.data.model

/**
 * Datos de la sesión del usuario activo.
 * Guarda la URL de la instancia, el nombre de usuario y la cookie de sesión para autenticar las peticiones.
 *
 * @property instanceUrl Dirección del servidor de BookWyrm (ej. bookwyrm.social).
 * @property username Nombre del usuario autenticado.
 * @property cookie Valor de la cookie de sesión usada para las llamadas HTTP.
 */
data class SessionData(
    val instanceUrl: String,
    val username: String,
    val cookie: String,
)

/**
 * Representa un elemento en el timeline de la aplicación.
 * Contiene toda la información necesaria para pintar una actividad (reseña, comentario, etc.) en la UI.
 *
 * @property id Identificador único de la actividad.
 * @property type Tipo de actividad (ej. "Review", "Comment", "Create").
 * @property published Fecha de publicación en formato texto.
 * @property content Contenido limpio de HTML para mostrar.
 * @property bookCoverUrl Enlace a la imagen de portada del libro si aplica.
 * @property bookUrl Enlace interno o de ActivityPub del libro.
 * @property actorName Nombre o usuario de quien realiza la actividad.
 * @property actorAvatarUrl Enlace a la imagen de perfil del autor.
 * @property objectId ID del objeto sobre el que se actúa (útil para interacciones como favoritos o respuestas).
 */
data class TimelineUiItem(
    val id: String,
    val type: String,
    val published: String,
    val content: String,
    // Portada del libro relacionado con la actividad (null si no aplica)
    val bookCoverUrl: String? = null,
    // URL del libro para posible navegación futura
    val bookUrl: String? = null,
    // Autor real de la actividad (puede ser un seguido, no siempre el usuario propio)
    val actorName: String = "",
    val actorAvatarUrl: String? = null,
    // ID real del objeto de la actividad (necesario para responder o dar Like al objeto, no a la acción)
    val objectId: String = ""
)

/**
 * Representa un usuario en la lista de seguidores o seguidos.
 * Contiene información de perfil básica y el estado de seguimiento.
 *
 * @property actorUrl URL única de ActivityPub para el actor/usuario.
 * @property name Nombre para mostrar del usuario.
 * @property handle Identificador completo en formato @usuario@instancia.
 * @property summary Breve descripción o biografía del usuario.
 * @property avatarUrl URL de la foto de perfil.
 * @property isFollowedByMe Indica si el usuario actual sigue a esta persona.
 */
data class FollowUserItem(
    val actorUrl: String,
    val name: String,
    /** @username@instance — formato usado por los endpoints follow/unfollow de BookWyrm */
    val handle: String,
    val summary: String?,
    val avatarUrl: String?,
    val isFollowedByMe: Boolean
)


