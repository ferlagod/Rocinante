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
package com.ferlagod.rocinante.utils

import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem

/**
 * Resumen de lectura que se muestra en el perfil. Se calcula íntegramente con datos ya
 * cacheados —la estantería "Leídos" y la caché de enriquecimiento— así que no cuesta
 * ninguna petición de red.
 *
 * @property totalBooks libros en la estantería "Leídos".
 * @property booksThisYear libros con fecha de fin dentro del año en curso.
 * @property totalPages suma de páginas de los libros de la estantería (las que el .json trae).
 * @property booksPerYear recuento por año, ascendente y SIN huecos: los años intermedios sin
 *   lecturas aparecen con 0 para que el eje del gráfico no mienta sobre el paso del tiempo.
 * @property booksWithoutFinishDate libros que no entran en [booksPerYear] por no tener fecha
 *   de fin. Se muestra en la interfaz para que el gráfico no aparente ser el total.
 * @property booksWithoutPages libros cuyo Edition no trae número de páginas. No suman en
 *   [totalPages], así que la interfaz debe decirlo en lugar de presentar un total incompleto.
 * @property topAuthors autores más leídos, de más a menos libros (empates por orden
 *   alfabético para que la lista no baile entre aperturas).
 * @property booksWithoutAuthor libros de los que no se conoce el autor; no cuentan en
 *   [topAuthors] y la interfaz lo advierte.
 */
data class ReadingStats(
    val totalBooks: Int,
    val booksThisYear: Int,
    val totalPages: Int,
    val booksPerYear: List<YearCount>,
    val booksWithoutFinishDate: Int,
    val booksWithoutPages: Int,
    val topAuthors: List<AuthorCount>,
    val booksWithoutAuthor: Int
) {
    data class YearCount(val year: Int, val count: Int)

    data class AuthorCount(val name: String, val count: Int)

    /** Un solo año no es una serie temporal: no merece gráfico, solo los totales. */
    val hasChartData: Boolean get() = booksPerYear.size >= 2

    /** Con un único autor el gráfico no compara nada. */
    val hasAuthorData: Boolean get() = topAuthors.size >= 2
}

object ReadingStatsCalculator {

    /** Años imposibles (errores de tecleo en BookWyrm, fechas a cero) que se descartan. */
    private const val MIN_PLAUSIBLE_YEAR = 1900

    /** Cuántos autores entran en el gráfico de los más leídos. */
    private const val TOP_AUTHORS = 10

    /**
     * BookWyrm entrega los autores de un libro en un solo texto separado por comas, y esa
     * coma es ambigua: "Linus Torvalds, David Diamond" son dos personas, pero "Henry, Ford"
     * es una sola escrita apellido primero. Se separa únicamente cuando *todas* las partes
     * parecen un nombre completo (llevan espacio dentro); en caso contrario se cuenta como
     * un solo autor, que es el error menos grave: agrupar de más nunca inventa a alguien
     * que no existe, mientras que separar de más produce autores fantasma.
     */
    fun splitAuthors(authorName: String): List<String> {
        val parts = authorName.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return listOf(authorName.trim()).filter { it.isNotEmpty() }
        return if (parts.all { it.contains(' ') }) parts else listOf(authorName.trim())
    }

    /**
     * @param books estantería "Leídos" tal y como la cachea `TimelineCache.loadShelfBooks`.
     * @param enrichment caché de enriquecimiento indexada por id de libro; de aquí sale la
     *   fecha de fin en ISO ("2025-01-01"), que es el único formato fiable (el texto visible
     *   de BookWyrm está localizado y no se debe parsear).
     * @param currentYear año en curso; se pasa como parámetro para poder probar la función.
     */
    fun compute(
        books: List<ShelfBookItem>,
        enrichment: Map<String, BookEnrichment>,
        currentYear: Int
    ): ReadingStats {
        val years = books.mapNotNull { book ->
            val finished = book.id?.let { enrichment[it]?.finished }
            finished?.take(4)?.toIntOrNull()?.takeIf { it in MIN_PLAUSIBLE_YEAR..currentYear }
        }

        val counts = years.groupingBy { it }.eachCount()
        val perYear = if (counts.isEmpty()) {
            emptyList()
        } else {
            // Se rellenan los huecos para que el eje horizontal sea continuo.
            (counts.keys.min()..counts.keys.max()).map { year ->
                ReadingStats.YearCount(year, counts[year] ?: 0)
            }
        }

        val authorNames = books.mapNotNull { book ->
            book.id?.let { enrichment[it]?.authorName }?.takeIf { it.isNotBlank() }
        }
        val authorCounts = authorNames
            .flatMap { splitAuthors(it) }
            .groupingBy { it }
            .eachCount()
        val topAuthors = authorCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_AUTHORS)
            .map { ReadingStats.AuthorCount(it.key, it.value) }

        return ReadingStats(
            totalBooks = books.size,
            booksThisYear = counts[currentYear] ?: 0,
            totalPages = books.sumOf { it.pages ?: 0 },
            booksPerYear = perYear,
            booksWithoutFinishDate = books.size - years.size,
            booksWithoutPages = books.count { (it.pages ?: 0) <= 0 },
            topAuthors = topAuthors,
            booksWithoutAuthor = books.size - authorNames.size
        )
    }
}
