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

import com.google.gson.annotations.SerializedName

/**
 * Representa el perfil público y la información básica de un actor/usuario
 * en BookWyrm, incluyendo contadores de seguidores extraídos.
 */
data class BookWyrmProfile(
    val id: String?,
    val type: String?,
    val name: String?,
    val summary: String?,
    val outbox: String?,
    val inbox: String?,
    val icon: ProfileIcon?,
    // preferredUsername es el handle local (sin @instance) según la spec ActivityPub
    val preferredUsername: String?,
    // Se cambia de Int a String para capturar las URLs federadas de las colecciones
    val followers: String?,
    val following: String?,
    // Campos locales para la interfaz gráfica (no vienen del JSON de perfil)
    var followersCountLocal: Int? = null,
    var followingCountLocal: Int? = null,
    var readingGoal: ReadingGoal? = null
)

/**
 * Modelo para representar el icono de perfil de un usuario.
 */
data class ProfileIcon(
    val url: String?
)

/**
 * Representa el progreso del reto de lectura anual extraído del HTML de BookWyrm.
 */
data class ReadingGoal(
    val max: Int,
    val value: Int
)

data class ActivityPubCollection(
    @SerializedName("totalItems") val totalItems: Int?
)

/**
 * Representa una página del 'Outbox' (Bandeja de salida) de ActivityPub, contiene actividades.
 */
data class OutboxPage(
    @SerializedName("orderedItems") val orderedItems: List<ActivityPubActivity>?
)

/**
 * Representa una actividad individual en el estándar ActivityPub (Nota, Reseña, Artículo, etc.).
 */
data class ActivityPubActivity(
    val id: String?,
    val type: String?,
    val published: String?,
    val actor: String?,
    val content: String? = null,
    val name: String? = null,
    @SerializedName("object") val rawObjectData: com.google.gson.JsonElement? = null,
    val actorAvatarUrl: String? = null
) {
    val objectData: ActivityPubObject?
        get() = if (rawObjectData != null && rawObjectData.isJsonObject) {
            com.google.gson.GsonBuilder().setLenient().create().fromJson(rawObjectData, ActivityPubObject::class.java)
        } else null
}

/**
 * Objeto genérico de ActivityPub (puede ser una nota, reseña, etc.).
 */
data class ActivityPubObject(
    val id: String?,
    val type: String?,
    val content: String?,
    val name: String?,
    // Calificación por estrellas (Review)
    val rating: Int?,
    // URL del libro al que hace referencia la actividad (Review, Comment, Quotation)
    @SerializedName("inReplyToBook") val inReplyToBook: String? = null,
    // Adjuntos: BookWyrm puede incluir aquí la portada del libro
    val attachment: List<ActivityPubAttachment>? = null,
    // Portada directa si el objeto es un Libro (Edition, Work, etc)
    val cover: ActivityPubAttachment? = null
)

/**
 * Adjunto multimedia dentro de una actividad de ActivityPub.
 */
data class ActivityPubAttachment(
    val url: String? = null,
    val mediaType: String? = null,
    val name: String? = null
)

// Colección de usuarios seguidos (ActivityPub OrderedCollection)
/**
 * Representa una página de usuarios seguidos o seguidores.
 */
data class FollowingPage(
    @SerializedName("orderedItems") val orderedItems: List<String>?
)

data class BookSearchResult(
    val key: String?,
    val title: String?,
    val author: String?,
    val year: Int?,
    val cover: String?,
    val isRemote: Boolean = false,
    val remoteId: String? = null
)

/**
 * Representa los detalles ampliados y estructurados de un libro
 * devueltos por la API de BookWyrm.
 */
data class BookWyrmBookDetails(
    val title: String?,
    val description: String?,
    val publishedDate: String?,
    val pages: Int?,
    // Nuevo campo para capturar la URL de la portada en la ficha
    val cover: ShelfBookCover?
)

// NUEVOS MODELOS PARA LA ESTANTERÍA
/**
 * Representa una estantería (colección de libros) de un usuario.
 */
data class ShelfPage(
    @SerializedName("orderedItems") val orderedItems: List<ShelfBookItem>?
)

data class ShelfBookItem(
    val id: String?,
    val title: String?,
    val cover: ShelfBookCover?
)

/**
 * Representación de la portada de un libro.
 */
data class ShelfBookCover(
    val url: String?
)

data class SuggestedUser(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val profileUrl: String
)
