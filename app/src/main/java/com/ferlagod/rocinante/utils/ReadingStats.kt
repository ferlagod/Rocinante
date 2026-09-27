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
 * @property fastestRead la lectura más corta y [slowestRead] la más larga, de entre las que
 *   tienen fecha de inicio **y** de fin; sin las dos no hay nada que medir. Son null si no
 *   hay ninguna con las dos fechas.
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
    val pagesThisYear: Int = 0,
    val avgPagesPerBook: Double? = null,
    val avgPagesPerBookThisYear: Double? = null,
    val uniqueBooksCount: Int = totalBooks,
    val rereadsCount: Int = 0,
    val booksPerYear: List<YearCount>,
    val booksPerMonthThisYear: List<MonthCount> = emptyList(),
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
    val fastestRead: ReadSpan?,
    val slowestRead: ReadSpan?,
    val longestBook: BookPagesSpan? = null,
    val shortestBook: BookPagesSpan? = null,
    val languageDistribution: List<LanguageCount>,
    val booksWithoutLanguage: Int,
    val formatDistribution: List<FormatCount>,
    val booksWithoutFormat: Int,
    val filterYear: Int? = null
) {
    data class YearCount(val year: Int, val count: Int)

    data class MonthCount(val month: Int, val count: Int)

    data class AuthorCount(val name: String, val count: Int)

    data class RatingBucket(val rating: Double, val count: Int)

    /**
     * @property label grafía del idioma más frecuente en la propia estantería.
     * @property flag bandera del idioma, o null si no hay ninguna asociada.
     */
    data class LanguageCount(val label: String, val flag: String?, val count: Int)

    /**
     * Una lectura con lo que tardó, en días enteros contando los dos extremos: empezar y
     * terminar el mismo día es un día, no cero, igual que en la tarjeta de la estantería.
     */
    data class ReadSpan(val book: ShelfBookItem, val days: Int)

    /**
     * Un libro con su número de páginas para los extremos de longitud (más largo / más corto).
     */
    data class BookPagesSpan(val book: ShelfBookItem, val pages: Int)

    data class FormatCount(val format: String, val count: Int)

    /** Un solo año no es una serie temporal: no merece gráfico, solo los totales. */
    val hasChartData: Boolean get() = booksPerYear.size >= 2

    /** Datos mensuales disponibles para el año consultado. */
    val hasMonthlyData: Boolean get() = booksPerMonthThisYear.any { it.count > 0 }

    /** Con un único autor el gráfico no compara nada. */
    val hasAuthorData: Boolean get() = topAuthors.size >= 2

    /** Sin ninguna valoración no hay media ni reparto que enseñar. */
    val hasRatingData: Boolean get() = ratedBooks > 0

    /** Sin libros con las dos fechas no se puede medir cuánto se tarda en leer. */
    val hasReadingDays: Boolean get() = booksWithReadingDays > 0

    /** Hay libros de distintas longitudes para mostrar extremos de páginas. */
    val hasPageExtremes: Boolean get() = longestBook != null && shortestBook != null && longestBook.pages != shortestBook.pages

    /** Hay lecturas extremas de tiempo o de páginas. */
    val hasExtremes: Boolean get() = (fastestRead != null && slowestRead != null) || hasPageExtremes

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

    /**
     * Días que duró una lectura, contando ambos extremos: leer y terminar el mismo día es
     * 1 día, no 0. Devuelve null si falta alguna fecha, no se entienden o el fin es anterior
     * al inicio. Vive aquí para que la estantería y la ficha del libro cuenten igual.
     */
    fun readingDays(startIso: String?, finishIso: String?): Int? {
        val start = parseIsoDate(startIso) ?: return null
        val finish = parseIsoDate(finishIso) ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, finish)
        if (days < 0) return null
        return days.toInt() + 1
    }

    /**
     * Un libro de los mejor valorados, con lo justo para pintarlo y saltar a su estantería.
     */
    data class TopRatedBook(
        val book: ShelfBookItem,
        val rating: Double,
        val finished: String?
    )

    /**
     * Los libros mejor valorados de la estantería «Leídos».
     *
     * Cuando hay más empatados en lo alto que sitios —diez con cinco estrellas para cinco
     * huecos— ganan los leídos más recientemente, así que el bloque va cambiando conforme
     * se lee en vez de quedarse congelado en los primeros que se puntuaron. Las fechas van
     * en ISO, que ordena bien como texto; los libros sin fecha quedan detrás de los que la
     * tienen con la misma nota.
     *
     * @param books estantería "Leídos" tal y como la cachea `TimelineCache.loadShelfBooks`.
     * @param enrichment caché de enriquecimiento indexada por id de libro: de ahí salen la
     *   valoración y la fecha de fin. Un libro sin valoración no entra.
     * @param limit cuántos devolver como mucho.
     * @param filterYear año concreto por el que filtrar, o null para todo el historial.
     */
    fun topRated(
        books: List<ShelfBookItem>,
        enrichment: Map<String, BookEnrichment>,
        limit: Int = 5,
        filterYear: Int? = null
    ): List<TopRatedBook> {
        val candidateBooks = if (filterYear != null) {
            books.filter { book ->
                val data = book.id?.let { enrichment[it] } ?: return@filter false
                val rts = data.readthroughs
                if (!rts.isNullOrEmpty()) {
                    rts.any { parseIsoDate(it.finished)?.year == filterYear }
                } else {
                    parseIsoDate(data.finished)?.year == filterYear
                }
            }
        } else {
            books
        }
        val rated = candidateBooks.mapNotNull { book ->
            val data = book.id?.let { enrichment[it] } ?: return@mapNotNull null
            val rating = data.rating ?: return@mapNotNull null
            TopRatedBook(book, rating, data.finished?.takeIf { it.isNotBlank() })
        }
        return rated
            .sortedWith(
                compareByDescending<TopRatedBook> { it.rating }
                    .thenByDescending { it.finished ?: "" }
            )
            .take(limit)
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
     * @param filterYear año por el que filtrar todas las estadísticas, o null para el historial completo.
     */
    fun compute(
        books: List<ShelfBookItem>,
        enrichment: Map<String, BookEnrichment>,
        currentYear: Int,
        filterYear: Int? = null
    ): ReadingStats {
        val targetYear = filterYear ?: currentYear

        // Recuento histórico completo de años para el gráfico de barras por años
        val allYears = books.flatMap { book ->
            val enriched = book.id?.let { enrichment[it] }
            val readthroughs = enriched?.readthroughs
            if (!readthroughs.isNullOrEmpty()) {
                readthroughs.mapNotNull { rt ->
                    parseIsoDate(rt.finished)?.year?.takeIf { it in MIN_PLAUSIBLE_YEAR..currentYear }
                }
            } else {
                val y = parseIsoDate(enriched?.finished)?.year?.takeIf { it in MIN_PLAUSIBLE_YEAR..currentYear }
                if (y != null) listOf(y) else emptyList()
            }
        }
        val allCounts = allYears.groupingBy { it }.eachCount()
        val perYear = if (allCounts.isEmpty()) {
            emptyList()
        } else {
            (allCounts.keys.min()..allCounts.keys.max()).map { year ->
                ReadingStats.YearCount(year, allCounts[year] ?: 0)
            }
        }

        // Si se filtra por año, determinamos qué libros y qué readthroughs corresponden a ese año
        data class BookWithReadCount(val book: ShelfBookItem, val readsInPeriod: Int, val finishedDates: List<String>)
        val booksWithPeriodReads: List<BookWithReadCount> = books.mapNotNull { book ->
            val enriched = book.id?.let { enrichment[it] }
            val readthroughs = enriched?.readthroughs
            if (filterYear == null) {
                val count = if (!readthroughs.isNullOrEmpty()) readthroughs.size else 1
                val dates = if (!readthroughs.isNullOrEmpty()) {
                    readthroughs.mapNotNull { it.finished }
                } else {
                    listOfNotNull(enriched?.finished)
                }
                BookWithReadCount(book, count, dates)
            } else {
                if (!readthroughs.isNullOrEmpty()) {
                    val matching = readthroughs.filter { parseIsoDate(it.finished)?.year == filterYear }
                    if (matching.isEmpty()) null
                    else BookWithReadCount(book, matching.size, matching.mapNotNull { it.finished })
                } else {
                    val finishedDate = enriched?.finished
                    if (parseIsoDate(finishedDate)?.year == filterYear) {
                        BookWithReadCount(book, 1, listOfNotNull(finishedDate))
                    } else {
                        null
                    }
                }
            }
        }

        val activeBooks = booksWithPeriodReads.map { it.book }
        val totalReads = booksWithPeriodReads.sumOf { it.readsInPeriod }
        val uniqueBooksCount = activeBooks.size
        val rereadsCount = (totalReads - uniqueBooksCount).coerceAtLeast(0)

        // Páginas totales y del período
        var totalPages = 0
        var pagesThisYear = 0
        var booksWithoutPages = 0
        var totalBooksWithPages = 0
        var booksThisYearWithPages = 0

        booksWithPeriodReads.forEach { item ->
            val book = item.book
            val isAudiobook = book.physicalFormat?.equals("AudiobookFormat", ignoreCase = true) == true
            val pages = book.pages ?: 0
            if (pages > 0) {
                totalPages += pages * item.readsInPeriod
                totalBooksWithPages += item.readsInPeriod
            } else if (!isAudiobook) {
                booksWithoutPages += item.readsInPeriod
            }
        }

        // Páginas del año en curso (o año filtrado)
        books.forEach { book ->
            val enriched = book.id?.let { enrichment[it] }
            val readthroughs = enriched?.readthroughs
            val pages = book.pages ?: 0
            val readsInTargetYear = if (!readthroughs.isNullOrEmpty()) {
                readthroughs.count { parseIsoDate(it.finished)?.year == targetYear }
            } else {
                if (parseIsoDate(enriched?.finished)?.year == targetYear) 1 else 0
            }
            if (readsInTargetYear > 0 && pages > 0) {
                pagesThisYear += pages * readsInTargetYear
                booksThisYearWithPages += readsInTargetYear
            }
        }

        val avgPagesPerBook = if (totalBooksWithPages > 0) totalPages.toDouble() / totalBooksWithPages else null
        val avgPagesPerBookThisYear = if (booksThisYearWithPages > 0) pagesThisYear.toDouble() / booksThisYearWithPages else null

        // Desglose mensual para el año consultado (1..12)
        val finishedMonths = mutableMapOf<Int, Int>()
        books.forEach { book ->
            val enriched = book.id?.let { enrichment[it] }
            val readthroughs = enriched?.readthroughs
            val dates = if (!readthroughs.isNullOrEmpty()) {
                readthroughs.mapNotNull { it.finished }
            } else {
                listOfNotNull(enriched?.finished)
            }
            dates.forEach { dateStr ->
                val date = parseIsoDate(dateStr)
                if (date != null && date.year == targetYear) {
                    finishedMonths[date.monthValue] = (finishedMonths[date.monthValue] ?: 0) + 1
                }
            }
        }
        val booksPerMonthThisYear = (1..12).map { month ->
            ReadingStats.MonthCount(month, finishedMonths[month] ?: 0)
        }

        // Libros sin fecha de fin (o con fecha no plausible / futura)
        val booksWithoutFinishDate = if (filterYear == null) {
            (totalReads - allYears.size).coerceAtLeast(0)
        } else {
            0
        }

        // Autores más leídos
        val authorNames = activeBooks.mapNotNull { book ->
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

        // Valoraciones
        val ratings = activeBooks.mapNotNull { book ->
            book.id?.let { enrichment[it]?.rating }?.takeIf { it in 0.5..5.0 }
        }
        val ratingCounts = ratings.groupingBy { it }.eachCount()
        val ratingValues = ((1..5).map { it.toDouble() } + ratings.filter { it % 1.0 != 0.0 })
            .distinct()
            .sortedDescending()
        val distribution = ratingValues.map {
            ReadingStats.RatingBucket(it, ratingCounts[it] ?: 0)
        }

        // Tiempos de lectura (spans)
        val spans = booksWithPeriodReads.flatMap { item ->
            val book = item.book
            val enriched = book.id?.let { enrichment[it] } ?: return@flatMap emptyList()
            val readthroughs = enriched.readthroughs
            if (!readthroughs.isNullOrEmpty()) {
                val rts = if (filterYear != null) {
                    readthroughs.filter { parseIsoDate(it.finished)?.year == filterYear }
                } else {
                    readthroughs
                }
                rts.mapNotNull { rt ->
                    val start = parseIsoDate(rt.started) ?: return@mapNotNull null
                    val finish = parseIsoDate(rt.finished) ?: return@mapNotNull null
                    val days = java.time.temporal.ChronoUnit.DAYS.between(start, finish)
                    if (days < 0) null else days to finish.year
                }
            } else {
                val start = parseIsoDate(enriched.started) ?: return@flatMap emptyList()
                val finish = parseIsoDate(enriched.finished) ?: return@flatMap emptyList()
                val days = java.time.temporal.ChronoUnit.DAYS.between(start, finish)
                if (days < 0) emptyList() else listOf(days to finish.year)
            }
        }
        val spansThisYear = spans.filter { it.second == targetYear }

        // Lectura más rápida y más lenta
        val spansByBook = booksWithPeriodReads.flatMap { item ->
            val book = item.book
            val enriched = book.id?.let { enrichment[it] } ?: return@flatMap emptyList()
            val readthroughs = enriched.readthroughs
            val durations = if (!readthroughs.isNullOrEmpty()) {
                val rts = if (filterYear != null) {
                    readthroughs.filter { parseIsoDate(it.finished)?.year == filterYear }
                } else {
                    readthroughs
                }
                rts.mapNotNull { readingDays(it.started, it.finished) }
            } else {
                listOfNotNull(readingDays(enriched.started, enriched.finished))
            }
            durations.map { ReadingStats.ReadSpan(book, it) }
        }
        val byDuration = compareBy<ReadingStats.ReadSpan>({ it.days }, { it.book.title ?: "" })
        val fastestRead = spansByBook.minWithOrNull(byDuration)
        val slowestRead = spansByBook.maxWithOrNull(
            compareBy<ReadingStats.ReadSpan> { it.days }
                .thenByDescending { it.book.title ?: "" }
        )

        // Extremos de longitud (libro más largo y más corto con páginas conocidas)
        val validPageBooks = activeBooks.filter { (it.pages ?: 0) > 0 }
        val longestBook = validPageBooks.maxWithOrNull(
            compareBy<ShelfBookItem>({ it.pages ?: 0 }, { it.title ?: "" })
        )?.let { ReadingStats.BookPagesSpan(it, it.pages ?: 0) }
        val shortestBook = validPageBooks.minWithOrNull(
            compareBy<ShelfBookItem>({ it.pages ?: 0 }, { it.title ?: "" })
        )?.let { ReadingStats.BookPagesSpan(it, it.pages ?: 0) }

        // Idiomas
        val languageSpellings = mutableMapOf<String, MutableList<String>>()
        var booksWithLanguage = 0
        activeBooks.forEach { book ->
            val languages = book.languages.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            if (languages.isEmpty()) return@forEach
            booksWithLanguage++
            languages
                .map { it to (LanguageFlags.flagFor(it) ?: it.lowercase()) }
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

        // Formatos
        val formats = activeBooks.mapNotNull { it.physicalFormat?.trim()?.takeIf { f -> f.isNotEmpty() } }
        val formatDistribution = formats.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReadingStats.FormatCount(it.key, it.value) }

        return ReadingStats(
            totalBooks = totalReads,
            booksThisYear = allCounts[targetYear] ?: 0,
            totalPages = totalPages,
            pagesThisYear = pagesThisYear,
            avgPagesPerBook = avgPagesPerBook,
            avgPagesPerBookThisYear = avgPagesPerBookThisYear,
            uniqueBooksCount = uniqueBooksCount,
            rereadsCount = rereadsCount,
            booksPerYear = perYear,
            booksPerMonthThisYear = booksPerMonthThisYear,
            booksWithoutFinishDate = booksWithoutFinishDate,
            booksWithoutPages = booksWithoutPages,
            topAuthors = topAuthors,
            booksWithoutAuthor = activeBooks.size - authorNames.size,
            averageRating = ratings.average().takeIf { ratings.isNotEmpty() },
            ratedBooks = ratings.size,
            ratingDistribution = distribution,
            booksWithoutRating = activeBooks.size - ratings.size,
            avgReadingDaysThisYear = spansThisYear.map { it.first }.average()
                .takeIf { spansThisYear.isNotEmpty() },
            avgReadingDaysAllTime = spans.map { it.first }.average().takeIf { spans.isNotEmpty() },
            booksWithReadingDays = spans.size,
            fastestRead = fastestRead,
            slowestRead = slowestRead,
            longestBook = longestBook,
            shortestBook = shortestBook,
            languageDistribution = languageDistribution,
            booksWithoutLanguage = activeBooks.size - booksWithLanguage,
            formatDistribution = formatDistribution,
            booksWithoutFormat = activeBooks.size - formats.size,
            filterYear = filterYear
        )
    }
}
