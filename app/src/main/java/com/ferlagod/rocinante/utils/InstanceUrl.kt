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

/**
 * La instancia tal y como la escribe la gente, convertida en la URL base con la que trabaja la
 * app. Se pega de todo: con espacios, en mayúsculas, el handle entero (`@yo@instancia.social`),
 * o la dirección de una página (`https://instancia.social/login`), que antes acababa en
 * `/login/login`.
 */
object InstanceUrl {

    /**
     * @return "esquema://host[:puerto]" sin barra final, o "" si no queda nada utilizable.
     *   Se respeta `http://` si se escribe a propósito; si no, `https://`.
     */
    fun normalize(raw: String): String {
        var text = raw.trim().replace(Regex("\\s+"), "")
        if (text.isEmpty()) return ""

        val scheme = when {
            text.startsWith("http://", ignoreCase = true) -> "http"
            else -> "https"
        }
        text = text.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")

        // Handle completo: @usuario@instancia o usuario@instancia.
        if (!text.contains('/') && text.contains('@')) {
            text = text.substringAfterLast('@')
        }

        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
            .trimEnd('.')
            .lowercase()
        if (authority.isEmpty()) return ""
        return "$scheme://$authority"
    }
}
