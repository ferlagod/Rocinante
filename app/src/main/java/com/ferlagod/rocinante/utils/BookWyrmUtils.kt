/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 Fernando Lago (ferlagod)
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU Affero General Public License (AGPLv3).
 * * AVISO DE DOBLE LICENCIA: Para uso comercial o propietario sin 
 * las obligaciones de liberación de código de la AGPLv3, 
 * es necesario adquirir una licencia comercial previa.
 */
package com.ferlagod.rocinante.utils

object BookWyrmUtils {

    /**
     * Extrae el identificador de la obra a partir de su URL completa.
     * Ejemplo: "https://instancia.com/book/12345.json" -> "12345"
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
     * Garantiza que la dirección incluya el sufijo necesario para obtener datos estructurados.
     */
    fun ensureJsonUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val cleanUrl = url.trim()
        return if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl.json"
    }
}