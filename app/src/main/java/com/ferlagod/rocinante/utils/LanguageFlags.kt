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
 * Traduce el nombre de un idioma (tal y como lo entrega BookWyrm, en inglés o en su
 * forma nativa: "Danish", "Dansk", "English"…) a un emoji de bandera para mostrarlo
 * de forma compacta en la lista.
 *
 * AVISO: un idioma no equivale a un país. Para idiomas hablados en varios países
 * (inglés, español, árabe, chino…) se elige una bandera por defecto. Si no hay
 * correspondencia, se devuelve null y quien lo use debe recurrir al texto del idioma.
 */
object LanguageFlags {

    /**
     * @return el emoji de bandera del idioma, o null si no hay correspondencia
     *         (en cuyo caso conviene mostrar el texto del idioma como reserva).
     */
    fun flagFor(language: String?): String? {
        if (language.isNullOrBlank()) return null
        return MAP[language.trim().lowercase()]
    }

    // Clave = nombre en minúsculas (inglés y/o forma nativa) -> emoji de bandera.
    // Idiomas ambiguos (varios países) usan una bandera por defecto, comentada como tal.
    private val MAP: Map<String, String> = mapOf(
        "english" to "🇬🇧",            // por defecto (también 🇺🇸)
        "danish" to "🇩🇰", "dansk" to "🇩🇰",
        "swedish" to "🇸🇪", "svenska" to "🇸🇪",
        "norwegian" to "🇳🇴", "norsk" to "🇳🇴",
        "finnish" to "🇫🇮", "suomi" to "🇫🇮",
        "german" to "🇩🇪", "deutsch" to "🇩🇪",
        "french" to "🇫🇷", "français" to "🇫🇷", "francais" to "🇫🇷",
        "spanish" to "🇪🇸", "español" to "🇪🇸", "espanol" to "🇪🇸", // por defecto (también 🇲🇽…)
        "italian" to "🇮🇹", "italiano" to "🇮🇹",
        "dutch" to "🇳🇱", "nederlands" to "🇳🇱",
        "portuguese" to "🇵🇹", "português" to "🇵🇹", "portugues" to "🇵🇹", // por defecto (también 🇧🇷)
        "polish" to "🇵🇱", "polski" to "🇵🇱",
        "czech" to "🇨🇿", "čeština" to "🇨🇿", "cestina" to "🇨🇿",
        "greek" to "🇬🇷", "ελληνικά" to "🇬🇷",
        "romanian" to "🇷🇴", "română" to "🇷🇴", "romana" to "🇷🇴",
        "ukrainian" to "🇺🇦", "українська" to "🇺🇦",
        "russian" to "🇷🇺", "русский" to "🇷🇺",
        "icelandic" to "🇮🇸", "íslenska" to "🇮🇸",
        "hungarian" to "🇭🇺", "magyar" to "🇭🇺",
        "turkish" to "🇹🇷", "türkçe" to "🇹🇷", "turkce" to "🇹🇷",
        "japanese" to "🇯🇵", "日本語" to "🇯🇵",
        "chinese" to "🇨🇳", "中文" to "🇨🇳",              // por defecto (también 🇹🇼)
        "korean" to "🇰🇷", "한국어" to "🇰🇷",
        "arabic" to "🇸🇦", "العربية" to "🇸🇦",           // por defecto (varios países)
        "hindi" to "🇮🇳", "हिन्दी" to "🇮🇳"
        // Catalán y gallego se omiten a propósito (sin bandera de emoji estándar): reserva a texto.
    )
}
