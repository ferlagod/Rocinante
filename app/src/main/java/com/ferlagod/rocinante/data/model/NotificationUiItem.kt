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
