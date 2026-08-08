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
 * Un recorte de la estantería, el que se pide al tocar una gráfica del perfil: los libros de
 * un año, los de una nota, los de un idioma o los de un formato.
 *
 * Cada gráfica cuenta los libros a su manera —el año sale de las fechas de lectura, el idioma
 * agrupa «Danish» y «Dansk» bajo la misma bandera—, y el recorte tiene que quedarse
 * exactamente con los que esa gráfica contó. Por eso la regla vive aquí y no en la pantalla:
 * es la misma que usa [ReadingStatsCalculator], se prueba sin Android delante, y así la lista
 * no puede decir siete libros donde la barra decía ocho.
 */
sealed interface ShelfFilter {

    /** Libros terminados en un año concreto. Una relectura cuenta en el año en que terminó. */
    data class Year(val year: Int) : ShelfFilter

    /** Libros con esa nota exacta, en la escala de media estrella de BookWyrm. */
    data class Rating(val stars: Double) : ShelfFilter

    /**
     * Libros en ese idioma. Se guarda la grafía visible; la comparación va por la clave con
     * la que agrupa el perfil, así que «Danish» encuentra también los que ponen «Dansk».
     */
    data class Language(val label: String) : ShelfFilter

    /** Libros en ese formato, con el valor tal cual lo da BookWyrm ("Hardcover", "EBook"...). */
    data class Format(val name: String) : ShelfFilter
}

object ShelfFiltering {

    /**
     * La clave con la que se agrupan los idiomas: la bandera si la hay, y si no el texto en
     * minúsculas. Es la misma que usa el perfil al construir su gráfica, y es lo que hace que
     * una estantería que mezcla «Danish» y «Dansk» cuente un idioma y no dos.
     */
    fun languageKey(language: String): String {
        val clean = language.trim()
        return LanguageFlags.flagFor(clean) ?: clean.lowercase()
    }

    /**
     * Los años en que se terminó este libro. Son varios si hay relecturas, y ninguno mientras
     * no se sepa la fecha. Se descartan los años imposibles igual que en las estadísticas: una
     * fecha estropeada en la instancia metería una barra en el año 200.
     */
    fun finishedYears(enrichment: BookEnrichment?): List<Int> {
        val readthroughs = enrichment?.readthroughs
        val raw = if (!readthroughs.isNullOrEmpty()) {
            readthroughs.mapNotNull { it.finished }
        } else {
            listOfNotNull(enrichment?.finished)
        }
        return raw.mapNotNull { it.take(4).toIntOrNull() }
    }

    /**
     * ¿Entra este libro en el recorte?
     *
     * @param book libro de la estantería.
     * @param enrichment sus datos por libro, o null si todavía no se han leído. Sin ellos no se
     *   sabe ni la nota ni la fecha, así que esos dos recortes lo dejan fuera en vez de colarlo.
     */
    fun matches(book: ShelfBookItem, enrichment: BookEnrichment?, filter: ShelfFilter): Boolean =
        when (filter) {
            is ShelfFilter.Year -> filter.year in finishedYears(enrichment)

            is ShelfFilter.Rating -> enrichment?.rating == filter.stars

            is ShelfFilter.Language -> {
                val wanted = languageKey(filter.label)
                book.languages.orEmpty().any { it.isNotBlank() && languageKey(it) == wanted }
            }

            is ShelfFilter.Format ->
                book.physicalFormat?.trim().orEmpty() == filter.name.trim() &&
                    filter.name.isNotBlank()
        }

    /** Los libros de la estantería que entran en el recorte, en el orden en que venían. */
    fun apply(
        books: List<ShelfBookItem>,
        enrichment: Map<String, BookEnrichment>,
        filter: ShelfFilter
    ): List<ShelfBookItem> = books.filter { book ->
        matches(book, book.id?.let { enrichment[it] }, filter)
    }
}
