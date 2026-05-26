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
package com.ferlagod.rocinante.data.model

data class SessionData(
    val instanceUrl: String,
    val username: String,
    val cookie: String,
)

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
 * [isFollowedByMe] indica si el usuario actualmente autenticado sigue a este actor.
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

