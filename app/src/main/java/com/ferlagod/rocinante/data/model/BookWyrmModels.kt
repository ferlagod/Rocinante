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
    @SerializedName("inReplyToBook") val inReplyToBook: String? = null,
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

// Serializable para poder conservar los resultados de búsqueda con rememberSaveable
// y que sobrevivan a cambios de configuración (p. ej. rotar la pantalla).
data class BookSearchResult(
    val key: String?,
    val title: String?,
    val author: String?,
    val year: Int?,
    val cover: String?,
    val isRemote: Boolean = false,
    val remoteId: String? = null
) : java.io.Serializable

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
    // Título normalizado por el servidor (artículos eliminados) para ordenar alfabéticamente.
    val sortTitle: String? = null,
    val cover: ShelfBookCover?,
    // Campos que ya vienen en el Edition del .json de la estantería (antes se descartaban):
    // número de páginas, idiomas y URLs de autores. No requieren peticiones adicionales.
    val pages: Int? = null,
    val languages: List<String>? = null,
    val authors: List<String>? = null,
    // Formato del ejemplar, también incluido en el Edition: BookWyrm usa los valores
    // "Hardcover", "Paperback", "EBook", "AudiobookFormat" y "GraphicNovel".
    val physicalFormat: String? = null
)

/**
 * Datos por libro que NO están en el .json de la estantería y se obtienen raspando
 * la página HTML del libro (una sola vez por libro, cacheados localmente): nombre del
 * autor, valoración del usuario (admite medias estrellas), y fechas de lectura en ISO.
 *
 * @property bookId URL/identificador del libro (misma clave que [ShelfBookItem.id]).
 * @property authorName Nombre legible del autor (el .json solo trae la URL).
 * @property rating Valoración del usuario (0.5–5.0) o null si no la ha valorado.
 * @property finished Fecha de fin de lectura en formato ISO (yyyy-MM-dd) o null.
 * @property started Fecha de inicio de lectura en formato ISO (yyyy-MM-dd) o null.
 * @property fetchedAt Marca de tiempo (epoch ms) de cuándo se obtuvo, para el resincronizado.
 */
data class BookEnrichment(
    val bookId: String,
    val authorName: String? = null,
    val rating: Double? = null,
    val finished: String? = null,
    val started: String? = null,
    // Idioma legible del libro (p. ej. "Danish"), leído de la página HTML del libro.
    val language: String? = null,
    val fetchedAt: Long? = null
)

/**
 * Representación de la portada de un libro.
 */
data class ShelfBookCover(
    val url: String?
)

// Serializable para conservar los resultados de búsqueda de usuarios al rotar.
data class SuggestedUser(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val profileUrl: String
) : java.io.Serializable
