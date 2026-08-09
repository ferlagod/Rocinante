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
 * Lo que devuelve la búsqueda de Open Library. Solo se piden los campos que se enseñan,
 * con `fields=`, porque la respuesta completa trae listas de cientos de identificadores de
 * archivo por libro y ocupa cien veces más que lo que se usa.
 */
data class OpenLibrarySearchResponse(
    @SerializedName("numFound") val numFound: Int = 0,
    @SerializedName("docs") val docs: List<OpenLibraryWork> = emptyList()
)

/**
 * Una obra de Open Library, no un ejemplar: agrupa todas sus ediciones en todos los idiomas.
 *
 * @property key Identificador con su ruta, «/works/OL17797130W».
 * @property editionCount Cuántas ediciones tiene. Sirve para avisar antes de abrir la lista:
 *   una obra popular pasa de las cien y hay que pedirlas de cincuenta en cincuenta.
 * @property coverId Identificador de la portada en el servidor de imágenes de Open Library.
 */
data class OpenLibraryWork(
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("author_name") val authorNames: List<String>? = null,
    @SerializedName("first_publish_year") val firstPublishYear: Int? = null,
    @SerializedName("edition_count") val editionCount: Int? = null,
    @SerializedName("cover_i") val coverId: Long? = null
) {
    /** El identificador a secas, «OL17797130W», que es lo que pide la ruta de ediciones. */
    val workId: String? get() = key?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /** Portada en tamaño medio. Open Library la sirve sin autenticación. */
    val coverUrl: String?
        get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
}

/**
 * La lista de ediciones de una obra.
 *
 * @property size Total de ediciones, que puede ser mucho mayor que las que trae esta página.
 */
data class OpenLibraryEditionsResponse(
    @SerializedName("size") val size: Int = 0,
    @SerializedName("entries") val entries: List<OpenLibraryEdition> = emptyList()
)

/**
 * Una edición concreta: es lo que se acaba importando, porque las estanterías de BookWyrm
 * guardan ejemplares y no obras.
 *
 * El idioma es lo que se busca aquí y es justo lo que falta más a menudo: en las obras que
 * medimos, un tercio de las ediciones no lo trae. Por eso se enseñan también editorial, año e
 * ISBN, que es con lo que se distinguen cuando no hay idioma.
 */
data class OpenLibraryEdition(
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("languages") val languages: List<OpenLibraryLanguageRef>? = null,
    @SerializedName("publishers") val publishers: List<String>? = null,
    @SerializedName("publish_date") val publishDate: String? = null,
    @SerializedName("number_of_pages") val pages: Int? = null,
    @SerializedName("covers") val covers: List<Long>? = null,
    @SerializedName("isbn_13") val isbn13: List<String>? = null,
    @SerializedName("isbn_10") val isbn10: List<String>? = null
) {
    /** Código del idioma en tres letras («dan»), o null si la edición no lo dice. */
    val languageCode: String?
        get() = languages?.firstOrNull()?.key?.trimEnd('/')?.substringAfterLast('/')

    /** El primer ISBN que tenga, prefiriendo el de trece. Es la llave para preguntar a la instancia. */
    val isbn: String?
        get() = isbn13?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: isbn10?.firstOrNull()?.takeIf { it.isNotBlank() }

    /**
     * La dirección que se le pasa a `resolve-book`. Tiene que ser la de la edición y no la de
     * la obra: dándole la obra, BookWyrm elige él la edición, y su preferencia por el inglés
     * no muerde cuando el idioma viene vacío, así que acaba trayendo cualquiera.
     */
    val remoteId: String?
        get() = key?.trimEnd('/')?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://openlibrary.org/books/$it" }

    val coverUrl: String?
        get() = covers?.firstOrNull { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
}

/**
 * Una materia del catálogo de Open Library, de las que se ofrecen al elegir.
 *
 * Lo que hace falta de aquí es la clave, y por eso se piden a ellos en vez de sacarlas de los
 * libros propios: la clave no se puede deducir del nombre. «World War, 1914-1918» parece
 * `world_war_1914_1918` y esa clave devuelve **una** obra; «Mystery & detective» parece
 * `mystery_detective` y devuelve **cero**. Dándola el servidor, la materia es exacta.
 *
 * @property workCount Cuántas obras tiene. Se enseña porque separa preguntas muy distintas que
 *   se escriben casi igual: «Fiction, historical, general» son 72.622 obras y «Historical
 *   fiction» son 4.655.
 */
data class OpenLibrarySubject(
    @SerializedName("key") val key: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("work_count") val workCount: Int = 0
) {
    /** La clave a secas, «historical_fiction», que es lo que entiende el campo `subject_key`. */
    val subjectKey: String?
        get() = key?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

/** Referencia al idioma, que Open Library da como ruta: `{"key": "/languages/dan"}`. */
data class OpenLibraryLanguageRef(
    @SerializedName("key") val key: String? = null
)
