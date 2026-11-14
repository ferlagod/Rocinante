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
import java.text.DateFormatSymbols
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Fechas en que se archivó cada libro, para los libros leídos que no tienen ninguna lectura
 * registrada.
 *
 * Es muy común en las importaciones (de Goodreads y similares) y al pasar un libro a «Leídos»
 * sin poner fechas: BookWyrm no guarda entonces ninguna fecha de fin, solo la del momento en
 * que entró en la estantería. Sin nada más, esos libros no salían en la gráfica por años. La
 * fecha de archivado no es la de fin de lectura, pero suele andar cerca, así que se usa como
 * aproximación, avisando de cuántas lo son.
 *
 * @property byUrl fecha ISO por dirección normalizada del libro (ver
 *   [com.ferlagod.rocinante.data.api.BookWyrmScraper.canonicalBookUrl]).
 * @property byTitle la misma fecha por título normalizado ([titleKey]). Los libros federados
 *   llegan en el .json con la dirección de su instancia de origen, pero la página de la
 *   estantería los enlaza con la local: el título es lo que tienen en común.
 */
data class ShelvedDates(
    val byUrl: Map<String, String> = emptyMap(),
    val byTitle: Map<String, String> = emptyMap()
) {
    /** La fecha de archivado de un libro de la estantería, o null si no se conoce. */
    fun dateFor(book: ShelfBookItem): String? {
        val url = book.id?.let { normalizeUrl(it) }
        return url?.let { byUrl[it] } ?: book.title?.let { byTitle[titleKey(it)] }
    }

    fun isEmpty(): Boolean = byUrl.isEmpty() && byTitle.isEmpty()

    companion object {
        /** Igual que canonicalBookUrl, sin depender de la sesión para poder probarlo. */
        fun normalizeUrl(url: String): String =
            url.substringBefore("/s/").removeSuffix(".json").trimEnd('/')

        fun titleKey(title: String): String = ShelvedDateParser.normalize(title).trim()
    }
}

/**
 * Lee la fecha que pinta BookWyrm en la columna «Archivado» de una estantería.
 *
 * BookWyrm la escribe con el filtro `naturalday` de Django, sin ninguna fecha legible por
 * máquina al lado: «today», «yesterday» o la fecha en el formato largo del idioma de quien
 * mira («Nov. 14, 2026», «14 de noviembre de 2026», «14. marraskuuta 2026», «2026年11月14日»).
 * Por eso se trocea en números y palabras, y la palabra se busca entre los nombres de los meses
 * del idioma de la página (el `lang` de `<html>`), y si no aparece, entre los de todos.
 */
object ShelvedDateParser {

    /** Idiomas de la interfaz de BookWyrm y de la aplicación. */
    private val LANGUAGES = listOf(
        "en", "es", "ca", "gl", "eu", "pt", "fr", "it", "de", "nl", "sv", "nb", "da", "fi",
        "pl", "cs", "ro", "lt", "el", "uk", "eo", "ko", "zh"
    )

    /** «Hoy» de `naturalday`, en los idiomas de BookWyrm y de la app, ya normalizado. */
    private val TODAY = setOf(
        "today", "hoy", "avui", "hoxe", "gaur", "hoje", "aujourdhui", "oggi", "heute", "vandaag",
        "idag", "tanaan", "dzisiaj", "dzis", "dnes", "astazi", "azi", "siandien", "σημερα",
        "сьогодні", "hodiau", "오늘", "今天"
    ).mapTo(mutableSetOf()) { compactWord(it) }

    /** «Ayer». */
    private val YESTERDAY = setOf(
        "yesterday", "ayer", "ahir", "onte", "atzo", "ontem", "hier", "ieri", "gestern",
        "gisteren", "igar", "eilen", "wczoraj", "vcera", "vakar", "χθες", "вчора", "hierau",
        "어제", "昨天"
    ).mapTo(mutableSetOf()) { compactWord(it) }

    /** «Mañana»: no debería salir en una fecha de archivado, pero `naturalday` lo tiene. */
    private val TOMORROW = setOf("tomorrow", "manana", "dema", "morgen", "huomenna", "jutro", "zitra").mapTo(mutableSetOf()) { compactWord(it) }

    /** Nombres de los meses de cada idioma, completos y abreviados, ya normalizados. */
    private val monthsByLanguage: Map<String, List<Pair<String, Int>>> by lazy {
        LANGUAGES.associateWith { monthNames(Locale.forLanguageTag(it)) }
    }

    private fun monthNames(locale: Locale): List<Pair<String, Int>> {
        val symbols = DateFormatSymbols.getInstance(locale)
        val names = mutableListOf<Pair<String, Int>>()
        listOf(symbols.months, symbols.shortMonths).forEach { list ->
            list.take(12).forEachIndexed { index, name ->
                // «1월», «11月»: el mes ya es un número y lo lee la vía numérica. Como palabra,
                // lo que queda («월») sería igual para los doce.
                if (name.any { it.isDigit() }) return@forEachIndexed
                val clean = normalize(name).filter { it.isLetter() }
                if (clean.length >= 3) names += clean to index + 1
            }
        }
        // El inglés de Django es el del estilo AP: «Sept.», que no está entre los de Java.
        if (locale.language == "en") names += "sept" to 9
        return names.distinct()
    }

    /**
     * Minúsculas y sin tildes, para comparar sin depender de la grafía. El apóstrofo separa,
     * como un espacio: el catalán escribe «d’octubre», y el mes es lo que va detrás.
     */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[’'ʼ`]"), " ")
    }

    /** La palabra como se compara: normalizada y sin espacios («i går» → «igar»). */
    private fun compactWord(text: String): String =
        normalize(text).trim().replace(Regex("\\s+"), "")

    /**
     * @param text lo que dice la celda.
     * @param language el `lang` de la página («fi», «pt-br»…), o null si no se sabe.
     * @param today el día de hoy, para «hoy» y «ayer».
     * @param defaultYear el año que se da por supuesto si el texto no lo trae, o null para
     *   exigirlo. BookWyrm lo omite en las fechas de publicación del año en curso («Aug. 22»);
     *   la fecha de archivado, en cambio, lo lleva siempre.
     * @return la fecha, o null si no se entiende.
     */
    fun parse(text: String, language: String?, today: LocalDate, defaultYear: Int? = null): LocalDate? {
        val clean = normalize(text).trim()
        if (clean.isEmpty()) return null
        when (compactWord(text)) {
            in TODAY -> return today
            in YESTERDAY -> return today.minusDays(1)
            in TOMORROW -> return today.plusDays(1)
        }

        val tokens = Regex("\\p{L}+|\\d+").findAll(clean).map { it.value }.toList()
        val numbers = tokens.mapIndexedNotNull { i, t -> t.toIntOrNull()?.let { i to it } }
        val words = tokens.filter { it.toIntOrNull() == null && it.length >= 3 }
        val month = words.firstNotNullOfOrNull { monthOf(it, language) }

        // Sin año escrito solo se acepta con el mes en letra: «22 de agosto» se entiende, pero
        // dos números sueltos no dicen cuál es el día y cuál el mes.
        val yearEntry = numbers.firstOrNull { (_, n) -> n in 1000..2999 }
            ?: if (defaultYear != null && month != null) -1 to defaultYear else return null
        val year = yearEntry.second
        val rest = numbers.filter { it.first != yearEntry.first }

        val (m, d) = if (month != null) {
            month to (rest.firstOrNull { (_, n) -> n in 1..31 }?.second ?: return null)
        } else {
            // Todo en números: «2026年11月14日», «2026-11-14» o «14.11.2026». Con el año delante
            // va mes y día; con el año detrás, día y mes, como en casi toda Europa.
            if (rest.size < 2) return null
            if (yearEntry.first < rest[0].first) rest[0].second to rest[1].second
            else rest[1].second to rest[0].second
        }
        return runCatching { LocalDate.of(year, m, d) }.getOrNull()
    }

    private fun monthOf(word: String, language: String?): Int? {
        val primary = language?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
            ?.let { if (it == "no" || it == "nn") "nb" else it }
        val own = primary?.let { monthsByLanguage[it] }
        own?.let { names -> match(word, names)?.let { return it } }
        // Sin idioma, o con uno que no conocemos: vale si todos los que encajan dicen lo mismo.
        val candidates = monthsByLanguage.values.mapNotNull { match(word, it) }.distinct()
        return candidates.singleOrNull()
    }

    private fun match(word: String, names: List<Pair<String, Int>>): Int? {
        names.firstOrNull { it.first == word }?.let { return it.second }
        // «marraskuuta» empieza por «marraskuu»; «sept» y «aza» son el principio del nombre.
        val byPrefix = names.filter { (name, _) -> word.startsWith(name) || name.startsWith(word) }
            .map { it.second }
            .distinct()
        return byPrefix.singleOrNull()
    }
}

/**
 * Lo que sale de completar las fechas de fin que faltan con la fecha de archivado.
 *
 * @property enrichment el enriquecimiento con esas fechas ya puestas como si fueran de fin.
 * @property approximated ids de los libros cuya fecha de fin es en realidad la de archivado.
 */
data class ShelvedFallback(
    val enrichment: Map<String, BookEnrichment>,
    val approximated: Set<String>
)

/**
 * Pone la fecha de archivado como fecha de fin a los libros que no tienen ninguna.
 *
 * Solo se toca un libro sin ninguna fecha de fin, ni suelta ni en sus lecturas: si tiene alguna,
 * esa manda. Se le quita también la de inicio, porque restarla de la de archivado daría una
 * duración inventada en los días de lectura.
 */
fun applyShelvedFallback(
    books: List<ShelfBookItem>,
    enrichment: Map<String, BookEnrichment>,
    shelved: ShelvedDates
): ShelvedFallback {
    if (shelved.isEmpty()) return ShelvedFallback(enrichment, emptySet())
    val result = enrichment.toMutableMap()
    val approximated = mutableSetOf<String>()
    books.forEach { book ->
        val id = book.id ?: return@forEach
        val data = enrichment[id]
        val hasFinish = !data?.finished.isNullOrBlank() ||
            data?.readthroughs.orEmpty().any { !it.finished.isNullOrBlank() }
        if (hasFinish) return@forEach
        val date = shelved.dateFor(book) ?: return@forEach
        result[id] = (data ?: BookEnrichment(bookId = id))
            .copy(finished = date, started = null, readthroughs = null)
        approximated += id
    }
    return ShelvedFallback(result, approximated)
}
