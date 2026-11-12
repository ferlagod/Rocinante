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

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Lógica pura del formulario de alta manual de libros (sin Android), para poder probarla.
 */
object CreateBookForm {

    /** Idiomas que ofrece el formulario, con el código ISO 639-1 del sistema al que equivalen. */
    private val LANGUAGE_BY_ISO = mapOf(
        "es" to "spa",
        "en" to "eng",
        "ca" to "cat",
        "gl" to "glg",
        "eu" to "eus",
        "fr" to "fra",
        "de" to "deu",
        "it" to "ita",
        "pt" to "por"
    )

    /**
     * El idioma del formulario que corresponde al del móvil («es» → «spa»), o null si el
     * formulario no lo ofrece.
     */
    fun languageCodeFor(isoLanguage: String?): String? =
        isoLanguage?.lowercase()?.substringBefore('-')?.substringBefore('_')?.let { LANGUAGE_BY_ISO[it] }

    private val DMY = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""")

    /** DD/MM/AAAA o DD-MM-AAAA pasa a AAAA-MM-DD; lo demás se deja como está (sin espacios). */
    fun normalizePublishedDate(raw: String): String {
        val trimmed = raw.trim()
        val match = DMY.find(trimmed) ?: return trimmed
        val (d, m, y) = match.destructured
        return "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
    }

    /**
     * Medianoche UTC del día escrito, para abrir el calendario en él. AAAA va al 1 de enero y
     * AAAA-MM al día 1. Null si no es una fecha.
     */
    fun publishedDateToUtcMillis(raw: String): Long? {
        val date = normalizePublishedDate(raw)
        val parts = date.split("-")
        val year = parts.getOrNull(0)?.takeIf { it.length == 4 }?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        if (parts.size > 3) return null
        return runCatching { LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrNull()
    }

    /** Minúsculas y sin tildes, para comparar nombres sin que importe cómo se escriban. */
    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /**
     * Los autores de [candidates] que casan con lo escrito: cada palabra de [query] tiene que
     * estar en el nombre, sin importar tildes ni mayúsculas. No se sugiere lo que ya está
     * escrito tal cual. Sin repetidos y como mucho [limit].
     */
    fun matchingAuthors(query: String, candidates: List<String?>, limit: Int = 5): List<String> {
        val words = fold(query).split(Regex("[\\s,.]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val typed = fold(query.trim())
        val seen = mutableSetOf<String>()
        return candidates.asSequence()
            .mapNotNull { it?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .filter { name ->
                val folded = fold(name)
                folded != typed && words.all { folded.contains(it) } && seen.add(folded)
            }
            .take(limit)
            .toList()
    }
}
