package com.ferlagod.rocinante.data.model

/**
 * Representa una notificación extraída mediante scraping de la interfaz web de BookWyrm.
 * Contiene la información visual necesaria para renderizar la notificación en la UI.
 *
 * @property id Identificador único de la notificación (puede generarse si no viene en el HTML).
 * @property isUnread Indica si la notificación es nueva (no leída).
 * @property type Tipo inferido de la notificación (Mención, Me gusta, etc.).
 * @property actorName Nombre en pantalla del usuario que generó la notificación.
 * @property actorAvatarUrl URL completa de la imagen de perfil del actor.
 * @property date Fecha/hora relativa o absoluta extraída del HTML.
 * @property content Contenido HTML original de la notificación.
 * @property permalink Enlace al estado o usuario que generó la notificación, si existe.
 */
data class NotificationUiItem(
    val id: String,
    val isUnread: Boolean,
    val type: NotificationType,
    val actorName: String,
    val actorAvatarUrl: String?,
    val date: String,
    val content: String,
    val permalink: String?
)

/**
 * Enumera los posibles tipos de notificaciones que genera BookWyrm.
 */
enum class NotificationType {
    /** Una respuesta a una publicación del usuario. */
    REPLY,
    /** Una mención directa al usuario en un estado. */
    MENTION,
    /** Un "Me gusta" (Favorito) en una publicación del usuario. */
    FAVORITE,
    /** Una publicación del usuario fue compartida (Boost). */
    BOOST,
    /** Un nuevo seguidor. */
    FOLLOW,
    /** Tipo desconocido o no parseable. */
    UNKNOWN
}
