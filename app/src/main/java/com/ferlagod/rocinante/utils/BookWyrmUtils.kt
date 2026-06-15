/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.utils

/**
 * Utilidades varias para interactuar y procesar datos del ecosistema BookWyrm / ActivityPub.
 */
object BookWyrmUtils {

    /**
     * Extrae el identificador de la obra (libro/edición) a partir de su URL completa de BookWyrm.
     * Ejemplo: "https://instancia.com/book/12345.json" -> "12345"
     *
     * @param url URL completa del libro.
     * @return El identificador numérico o de texto del libro, o cadena vacía si es inválido.
     */
    fun extractBookId(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val regex = """/book/(\d+)""".toRegex()
        val match = regex.find(url)
        if (match != null) {
            return match.groupValues[1]
        }
        return url.removeSuffix(".json").trimEnd('/').substringAfterLast("/")
    }

    /**
     * Garantiza que la dirección URL incluya el sufijo ".json" para solicitar datos estructurados.
     *
     * @param url URL original.
     * @return La URL con extensión ".json".
     */
    fun ensureJsonUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val cleanUrl = url.trim()
        return if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl.json"
    }

    // Abreviaturas de mes al estilo Django (formato `N`, AP-style) usadas por BookWyrm
    // al renderizar fechas de publicación. Solo necesitamos los 3 primeros caracteres.
    private val MONTH_ABBREVIATIONS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val REVIEW_DATE_REGEX = Regex("""([A-Za-z]+)\.?\s+(\d{1,2})(?:,\s*(\d{4}))?""")

    /**
     * Convierte la fecha legible que BookWyrm renderiza en las reseñas (p. ej.
     * "Aug. 22, 2024", "June 23, 2023" o "May 2" cuando el año es el actual) en una
     * [java.time.LocalDate] comparable, para poder ordenar las reseñas cronológicamente.
     *
     * Las cadenas ya en formato ISO ("2024-08-22T...") también se aceptan.
     *
     * @param raw Texto de fecha extraído del HTML.
     * @return La fecha parseada, o null si no se reconoce el formato.
     */
    fun parseReviewDate(raw: String?): java.time.LocalDate? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        // Caso ISO 8601 (por si una futura versión de BookWyrm expone datetime)
        if (s.length >= 10 && s[4] == '-' && s[7] == '-') {
            try {
                return java.time.LocalDate.parse(s.substring(0, 10))
            } catch (_: Exception) { /* continuar con el parseo legible */ }
        }

        val match = REVIEW_DATE_REGEX.find(s) ?: return null
        val month = MONTH_ABBREVIATIONS[match.groupValues[1].lowercase().take(3)] ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        // BookWyrm omite el año cuando la fecha pertenece al año en curso.
        val year = match.groupValues[3].toIntOrNull() ?: java.time.Year.now().value
        return try {
            java.time.LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }
}