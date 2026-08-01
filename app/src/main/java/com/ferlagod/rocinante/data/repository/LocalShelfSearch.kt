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

import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem
import java.text.Normalizer

/**
 * Un libro de las estanterías del usuario que coincide con lo buscado.
 *
 * @property book Libro tal y como está cacheado en la estantería.
 * @property shelfSlug Estantería en la que está ("to-read", "reading" o "read").
 * @property authorName Nombre del autor si está en la caché de enriquecimiento.
 */
data class LocalShelfHit(
    val book: ShelfBookItem,
    val shelfSlug: String,
    val authorName: String? = null
)

/**
 * Entrada del índice: un libro con sus textos ya normalizados. Se construye una vez al
 * cargar la caché para no volver a normalizar toda la estantería en cada pulsación.
 */
data class IndexedShelfBook(
    val hit: LocalShelfHit,
    val title: String,
    val sortTitle: String,
    val author: String
)

/**
 * Búsqueda dentro de las estanterías del usuario, sobre lo que ya hay en la caché local:
 * no hace ninguna petición de red, así que puede filtrar mientras se escribe.
 *
 * Nota: una estantería solo está en la caché una vez que se ha abierto al menos una vez,
 * igual que ocurre con las estadísticas del perfil. Hasta entonces no aporta resultados.
 */
object LocalShelfSearch {

    /** Orden en el que se presentan las estanterías cuando varias tienen coincidencias. */
    val SHELF_ORDER = listOf("reading", "to-read", "read")

    /** A partir de esta longitud se busca; con una sola letra casi todo coincidiría. */
    const val MIN_QUERY_LENGTH = 2

    /**
     * Letras que la descomposición Unicode no separa en base + acento y que conviene
     * plegar a mano para que "Sondergaard" encuentre "Søndergaard".
     */
    private val FOLDED = mapOf(
        'ø' to "o", 'æ' to "ae", 'ß' to "ss", 'đ' to "d", 'ð' to "d", 'ł' to "l", 'þ' to "th"
    )

    /**
     * Pasa un texto a minúsculas y le quita los acentos, de forma que "bronte" encuentre
     * "Brontë" y "sonderg" encuentre "Søndergaard".
     */
    fun normalize(text: String): String {
        val lower = text.lowercase()
        val folded = buildString(lower.length) {
            for (ch in lower) append(FOLDED[ch] ?: ch)
        }
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    /**
     * Prepara el índice de búsqueda a partir de las estanterías cacheadas.
     *
     * @param shelves Mapa slug→libros cacheados de esa estantería.
     * @param enrichment Datos por libro ya cacheados; de aquí sale el nombre del autor.
     */
    fun buildIndex(
        shelves: Map<String, List<ShelfBookItem>>,
        enrichment: Map<String, BookEnrichment> = emptyMap()
    ): List<IndexedShelfBook> {
        // Un libro puede estar en varias estanterías cacheadas (p. ej. si se movió de
        // estantería y la caché anterior sigue en disco): se queda en la primera del orden.
        val seen = mutableSetOf<String>()
        val index = mutableListOf<IndexedShelfBook>()
        for (slug in shelvesInOrder(shelves.keys)) {
            for (book in shelves[slug].orEmpty()) {
                val key = book.id ?: "$slug::${book.title}"
                if (!seen.add(key)) continue
                val authorName = book.id?.let { enrichment[it]?.authorName }
                index.add(
                    IndexedShelfBook(
                        hit = LocalShelfHit(book, slug, authorName),
                        title = normalize(book.title.orEmpty()),
                        sortTitle = normalize(book.sortTitle.orEmpty()),
                        author = normalize(authorName.orEmpty())
                    )
                )
            }
        }
        return index
    }

    /**
     * Filtra el índice buscando en el título y en el autor.
     *
     * La consulta se parte en palabras y se exigen todas (en cualquier orden), así que
     * "orwell 1984" encuentra el libro aunque título y autor vengan de campos distintos.
     *
     * @return Todas las coincidencias, ordenadas: primero las que empiezan por lo buscado,
     *         luego por estantería ([SHELF_ORDER]) y por título. Quien llame decide cuántas
     *         muestra.
     */
    fun search(query: String, index: List<IndexedShelfBook>): List<LocalShelfHit> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < MIN_QUERY_LENGTH) return emptyList()
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<Int, IndexedShelfBook>>()
        for (entry in index) {
            val haystack = listOf(entry.title, entry.sortTitle, entry.author)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (!tokens.all { haystack.contains(it) }) continue

            val rank = when {
                entry.title.startsWith(normalizedQuery) || entry.sortTitle.startsWith(normalizedQuery) -> 0
                entry.title.contains(normalizedQuery) || entry.sortTitle.contains(normalizedQuery) -> 1
                entry.author.startsWith(normalizedQuery) -> 2
                entry.author.contains(normalizedQuery) -> 3
                else -> 4
            }
            matches.add(rank to entry)
        }

        return matches
            .sortedWith(
                compareBy(
                    { it.first },
                    { SHELF_ORDER.indexOf(it.second.hit.shelfSlug).takeIf { i -> i >= 0 } ?: SHELF_ORDER.size },
                    { it.second.sortTitle.ifEmpty { it.second.title } }
                )
            )
            .map { it.second.hit }
    }

    /**
     * Atajo que construye el índice y busca de una vez. Cómodo para pruebas y para quien
     * solo busca una vez; la interfaz reutiliza el índice entre pulsaciones.
     */
    fun search(
        query: String,
        shelves: Map<String, List<ShelfBookItem>>,
        enrichment: Map<String, BookEnrichment> = emptyMap()
    ): List<LocalShelfHit> = search(query, buildIndex(shelves, enrichment))

    /** Estanterías conocidas primero y en orden; después cualquier otra que hubiera. */
    private fun shelvesInOrder(slugs: Set<String>): List<String> =
        SHELF_ORDER.filter { it in slugs } + slugs.filterNot { it in SHELF_ORDER }
}
