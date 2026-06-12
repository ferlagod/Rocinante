/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.utils

/**
 * Clase de utilidades para formatear texto y manipular strings con formato HTML.
 */
object HtmlUtils {

    /**
     * Limpia las etiquetas HTML de un texto y colapsa los espacios en blanco múltiples.
     * Útil para mostrar contenidos de reviews/comentarios limpios en la interfaz nativa.
     *
     * @param text Texto con posible formato HTML.
     * @return El texto limpio de etiquetas HTML.
     */
    fun stripHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.replace(Regex("<.*?>"), " ").replace("\\s+".toRegex(), " ").trim()
    }

    /**
     * Formatea una fecha de publicación (ISO) para mostrar sólo el componente de fecha (año-mes-día).
     *
     * @param value Cadena de fecha en formato ISO (ej. 2026-05-26T15:00:00Z).
     * @return La fecha recortada antes de la letra 'T', o null si la entrada es nula.
     */
    fun formatPublishedDate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.substringBefore("T")
    }

    /**
     * Convierte una cadena de fecha ISO 8601 en una descripción de tiempo relativo en español.
     * Ejemplos: "hace 5 minutos", "hace 2 horas", "ayer", "hace 3 días".
     * Para fechas más antiguas de 7 días, devuelve la fecha en formato "d MMM yyyy".
     *
     * @param value Cadena de fecha en formato ISO 8601 (ej. "2026-05-26T13:00:00Z").
     * @return Cadena de texto relativo, o null si la entrada es nula o no parseable.
     */
    fun formatRelativeDate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            val normalized = value.replace("Z", "+00:00")
            val instant = java.time.OffsetDateTime.parse(normalized).toInstant()
            val now = java.time.Instant.now()
            val diffSeconds = java.time.Duration.between(instant, now).seconds

            when {
                diffSeconds < 60 -> "ahora mismo"
                diffSeconds < 3600 -> {
                    val mins = diffSeconds / 60
                    "hace ${mins} ${if (mins == 1L) "minuto" else "minutos"}"
                }
                diffSeconds < 86400 -> {
                    val hours = diffSeconds / 3600
                    "hace ${hours} ${if (hours == 1L) "hora" else "horas"}"
                }
                diffSeconds < 172800 -> "ayer"
                diffSeconds < 604800 -> {
                    val days = diffSeconds / 86400
                    "hace ${days} días"
                }
                else -> {
                    // Para fechas más antiguas de 7 días: formato "26 may. 2026"
                    val localDate = java.time.OffsetDateTime.parse(normalized).toLocalDate()
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(
                        "d MMM yyyy",
                        java.util.Locale.forLanguageTag("es-ES")
                    )
                    localDate.format(formatter)
                }
            }
        } catch (_: Exception) {
            // Si no es parseable como ISO-8601, devolvemos el valor original.
            // Esto permite que el scraper pase strings como "hace 2 horas" o "18 May 2024"
            // directamente a la UI si el servidor HTML no nos da un ISO-8601 real.
            value
        }
    }
}