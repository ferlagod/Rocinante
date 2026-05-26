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

object HtmlUtils {
    fun stripHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.replace(Regex("<.*?>"), " ").replace("\\s+".toRegex(), " ").trim()
    }

    fun formatPublishedDate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.substringBefore("T")
    }
}