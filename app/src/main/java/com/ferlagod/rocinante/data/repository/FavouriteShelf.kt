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
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper

/**
 * Poner y quitar libros de las estanterías del usuario, y encontrar la que hace de favoritos.
 *
 * BookWyrm distingue cuatro estanterías suyas —«por leer», «leyendo», «leídos», «dejado a
 * medias»— de las que se crea el usuario. Las primeras son estados de lectura y se excluyen
 * entre sí; las demás son sitios donde guardar, y un libro puede estar en varias a la vez.
 *
 * Favoritos no es un concepto de BookWyrm: es una de esas estanterías propias, con un hjerte
 * delante en la aplicación. En la web se comporta como cualquier otra, que es justo lo que se
 * quiere.
 */
object FavouriteShelf {

    /** Cómo llega el libro a una estantería. */
    enum class Mode {
        /** Se añade y se queda además donde estuviera. */
        COPY,

        /** Se añade y se va de donde venía. Nunca desde «leídos». */
        MOVE
    }

    /**
     * Busca la estantería de favoritos entre las del usuario.
     *
     * @param stored Identificador que ya se conocía, o null. Se comprueba que siga existiendo:
     *   el usuario puede haberla borrado desde la web.
     * @param name Cómo se llama en el idioma de la aplicación.
     * @return Su identificador, o null si no se encuentra ninguna que encaje.
     */
    suspend fun find(
        api: BookWyrmApi,
        instanceUrl: String,
        username: String,
        stored: String?,
        name: String
    ): String? {
        val shelves = BookWyrmScraper.getUserShelves(api, instanceUrl, username)
        if (shelves.isEmpty()) return null
        stored?.takeIf { s -> shelves.any { it.identifier == s } }?.let { return it }
        val match = shelves.firstOrNull { !it.isSystem && it.name.equals(name, ignoreCase = true) }
        return match?.identifier
    }

    /**
     * Crea la estantería y devuelve su identificador.
     *
     * El identificador lo dice la propia respuesta: BookWyrm redirige a la estantería recién
     * hecha, así que sale de la cabecera `Location`. Antes se volvía a pedir la lista y se
     * buscaba por nombre, y eso creaba una estantería nueva en cada intento cuando la búsqueda
     * fallaba.
     *
     * @param privacy "public", "unlisted", "followers" o "direct".
     */
    suspend fun create(
        api: BookWyrmApi,
        bookUrl: String,
        instanceUrl: String,
        username: String,
        name: String,
        description: String = "",
        privacy: String = "direct"
    ): String? {
        // Nunca crear a ciegas: si ya hay una que encaje, esa. Sin esta comprobación, un
        // opslag fallido deja una estantería nueva por cada toque, y ya ha pasado dos veces.
        find(api, instanceUrl, username, stored = null, name = name)?.let { return it }

        val context = BookWyrmScraper.getReadDatesContext(api, bookUrl) ?: return null
        val response = api.createShelf(
            name = name,
            description = description,
            privacy = privacy,
            user = context.userId,
            csrfToken = context.csrfToken
        )
        // Un 200 es el formulario de vuelta porque algo no le valía; solo la redirección crea.
        if (response.code() != 302) return null
        return response.headers()["Location"]
            ?.trimEnd('/')
            ?.substringAfterLast("/books/")
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * ¿Está el libro en esta estantería?
     *
     * Lo dice la estantería, no la página del libro: esa solo menciona la de lectura.
     */
    suspend fun contains(
        api: BookWyrmApi,
        instanceUrl: String,
        username: String,
        identifier: String,
        bookUrl: String
    ): Boolean {
        val wanted = BookWyrmScraper.canonicalBookUrl(bookUrl)
        val base = (if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl")
            .trimEnd('/')
        val user = username.removePrefix("@").substringBefore("@").trim()
        for (page in 1..20) {
            val response = runCatching {
                api.getShelfData("$base/user/$user/books/$identifier.json?page=$page")
            }.getOrNull() ?: return false
            val items = response.orderedItems.orEmpty()
            if (items.isEmpty()) return false
            if (items.any { it.id != null && BookWyrmScraper.canonicalBookUrl(it.id) == wanted }) {
                return true
            }
        }
        return false
    }

    /**
     * Pone el libro en la estantería.
     *
     * @param from De dónde viene. Solo con [Mode.MOVE] se manda, y es lo único que separa
     *   copiar de mover: el mismo formulario con un campo más.
     */
    suspend fun put(
        api: BookWyrmApi,
        editionId: String,
        identifier: String,
        mode: Mode = Mode.COPY,
        from: String? = null
    ): Boolean {
        val response = api.shelveBook(
            bookId = editionId,
            shelfType = identifier,
            changeShelfFrom = from.takeIf { mode == Mode.MOVE && !it.isNullOrBlank() }
        )
        BookWyrmScraper.invalidateBookPage()
        return response.isSuccessful || response.code() == 302
    }

    /**
     * Saca el libro de la estantería. Necesita su número, que se lee de la página de la
     * estantería: no se puede deducir del identificador.
     */
    suspend fun remove(
        api: BookWyrmApi,
        instanceUrl: String,
        username: String,
        editionId: String,
        identifier: String,
        csrfToken: String
    ): Boolean {
        val number = BookWyrmScraper.getShelfNumber(api, instanceUrl, username, identifier)
            ?: return false
        val response = api.unshelveBook(editionId, number, csrfToken)
        BookWyrmScraper.invalidateBookPage()
        return response.isSuccessful || response.code() == 302
    }
}
