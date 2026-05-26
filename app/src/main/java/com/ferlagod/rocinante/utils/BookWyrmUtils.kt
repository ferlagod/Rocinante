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
}