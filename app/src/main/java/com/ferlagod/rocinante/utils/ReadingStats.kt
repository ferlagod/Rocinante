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
 * @property averageRating media de las valoraciones propias, o null si no hay ninguna.
 * @property ratedBooks cuántos libros llevan valoración (la base de [averageRating]).
 * @property ratingDistribution reparto de valoraciones, de mayor a menor. Incluye siempre
 *   las cinco estrellas enteras —aunque estén a cero, para que se vea la forma del
 *   reparto— y además las medias estrellas que realmente se han usado.
 * @property booksWithoutRating libros sin valorar; no entran en la media.
 * @property avgReadingDaysThisYear días de lectura por libro terminado este año, o null si
 *   ninguno tiene las dos fechas.
 * @property avgReadingDaysAllTime lo mismo para todos los años.
 * @property booksWithReadingDays libros con fecha de inicio Y de fin, que son los únicos que
 *   permiten medir cuánto se tardó. BookWyrm suele dejar vacía la de inicio, así que esta
 *   base es pequeña y la interfaz debe decir sobre cuántos libros se calcula la media.
 * @property languageDistribution idiomas leídos, de más a menos libros.
 * @property booksWithoutLanguage libros sin idioma declarado.
 * @property formatDistribution formatos leídos, con el valor tal cual lo da BookWyrm
 *   ("Hardcover", "EBook"…); traducirlo es cosa de la interfaz.
 * @property booksWithoutFormat libros sin formato declarado.
 */
data class ReadingStats(
    val totalBooks: Int,
    val booksThisYear: Int,
    val totalPages: Int,
    val booksPerYear: List<YearCount>,
    val booksWithoutFinishDate: Int,
    val booksWithoutPages: Int,
    val topAuthors: List<AuthorCount>,
    val booksWithoutAuthor: Int,
    val averageRating: Double?,
    val ratedBooks: Int,
    val ratingDistribution: List<RatingBucket>,
    val booksWithoutRating: Int,
    val avgReadingDaysThisYear: Double?,
    val avgReadingDaysAllTime: Double?,
    val booksWithReadingDays: Int,
    val languageDistribution: List<LanguageCount>,
    val booksWithoutLanguage: Int,
    val formatDistribution: List<FormatCount>,
    val booksWithoutFormat: Int
) {
    data class YearCount(val year: Int, val count: Int)

    data class AuthorCount(val name: String, val count: Int)

    data class RatingBucket(val rating: Double, val count: Int)

    /**
     * @property label grafía del idioma más frecuente en la propia estantería.
     * @property flag bandera del idioma, o null si no hay ninguna asociada.
     */
    data class LanguageCount(val label: String, val flag: String?, val count: Int)

    data class FormatCount(val format: String, val count: Int)

    /** Un solo año no es una serie temporal: no merece gráfico, solo los totales. */
    val hasChartData: Boolean get() = booksPerYear.size >= 2

    /** Con un único autor el gráfico no compara nada. */
    val hasAuthorData: Boolean get() = topAuthors.size >= 2

    /** Sin ninguna valoración no hay media ni reparto que enseñar. */
    val hasRatingData: Boolean get() = ratedBooks > 0

    /** Sin libros con las dos fechas no se puede medir cuánto se tarda en leer. */
    val hasReadingDays: Boolean get() = booksWithReadingDays > 0

    val hasLanguageData: Boolean get() = languageDistribution.isNotEmpty()

    val hasFormatData: Boolean get() = formatDistribution.isNotEmpty()
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
    /** "2025-01-01" (o "2025-01-01T…") → fecha; null si BookWyrm no la trae o es ilegible. */
    private fun parseIsoDate(value: String?): java.time.LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.LocalDate.parse(value.take(10)) }.getOrNull()
    }

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

        val ratings = books.mapNotNull { book ->
            book.id?.let { enrichment[it]?.rating }?.takeIf { it in 0.5..5.0 }
        }
        val ratingCounts = ratings.groupingBy { it }.eachCount()
        // Las cinco estrellas enteras salen siempre, aunque estén a cero: así se ve la forma
        // del reparto. Las medias solo si se han usado, para no llenar el gráfico de huecos.
        val ratingValues = ((1..5).map { it.toDouble() } + ratings.filter { it % 1.0 != 0.0 })
            .distinct()
            .sortedDescending()
        val distribution = ratingValues.map {
            ReadingStats.RatingBucket(it, ratingCounts[it] ?: 0)
        }

        // Días de lectura: solo los libros que traen las dos fechas. Se descartan los tramos
        // negativos (fechas invertidas al teclearlas), que falsearían la media.
        val spans = books.mapNotNull { book ->
            val enriched = book.id?.let { enrichment[it] } ?: return@mapNotNull null
            val start = parseIsoDate(enriched.started) ?: return@mapNotNull null
            val finish = parseIsoDate(enriched.finished) ?: return@mapNotNull null
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, finish)
            if (days < 0) null else days to finish.year
        }
        val spansThisYear = spans.filter { it.second == currentYear }

        // Idiomas: se agrupan por BANDERA, no por el texto. BookWyrm guarda el idioma tal y
        // como venga en la edición, así que una misma estantería mezcla "Danish" y "Dansk";
        // agrupar por el texto los contaría como dos idiomas distintos. Como etiqueta se usa
        // la grafía más repetida. Los idiomas sin bandera se agrupan por su texto en minúsculas.
        val languageSpellings = mutableMapOf<String, MutableList<String>>()
        var booksWithLanguage = 0
        books.forEach { book ->
            val languages = book.languages.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            if (languages.isEmpty()) return@forEach
            booksWithLanguage++
            languages
                .map { it to (LanguageFlags.flagFor(it) ?: it.lowercase()) }
                // Un libro solo cuenta una vez por idioma, aunque liste "Danish" y "Dansk".
                .distinctBy { (_, key) -> key }
                .forEach { (spelling, key) ->
                    languageSpellings.getOrPut(key) { mutableListOf() }.add(spelling)
                }
        }
        val languageDistribution = languageSpellings
            .map { (_, spellings) ->
                val label = spellings.groupingBy { it }.eachCount()
                    .maxWithOrNull(compareBy({ it.value }, { it.key }))!!.key
                ReadingStats.LanguageCount(label, LanguageFlags.flagFor(label), spellings.size)
            }
            .sortedWith(compareByDescending<ReadingStats.LanguageCount> { it.count }.thenBy { it.label })

        val formats = books.mapNotNull { it.physicalFormat?.trim()?.takeIf { f -> f.isNotEmpty() } }
        val formatDistribution = formats.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReadingStats.FormatCount(it.key, it.value) }

        return ReadingStats(
            totalBooks = books.size,
            booksThisYear = counts[currentYear] ?: 0,
            totalPages = books.sumOf { it.pages ?: 0 },
            booksPerYear = perYear,
            booksWithoutFinishDate = books.size - years.size,
            booksWithoutPages = books.count { (it.pages ?: 0) <= 0 },
            topAuthors = topAuthors,
            booksWithoutAuthor = books.size - authorNames.size,
            averageRating = ratings.average().takeIf { ratings.isNotEmpty() },
            ratedBooks = ratings.size,
            ratingDistribution = distribution,
            booksWithoutRating = books.size - ratings.size,
            avgReadingDaysThisYear = spansThisYear.map { it.first }.average()
                .takeIf { spansThisYear.isNotEmpty() },
            avgReadingDaysAllTime = spans.map { it.first }.average().takeIf { spans.isNotEmpty() },
            booksWithReadingDays = spans.size,
            languageDistribution = languageDistribution,
            booksWithoutLanguage = books.size - booksWithLanguage,
            formatDistribution = formatDistribution,
            booksWithoutFormat = books.size - formats.size
        )
    }
}
