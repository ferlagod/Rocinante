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
 * Utilidades varias para interactuar y procesar datos del ecosistema BookWyrm / ActivityPub.
 */
object BookWyrmUtils {

    /**
     * ¿Es esta respuesta una redirección «buena», de las que da Django al guardar un formulario?
     *
     * No lo es la que lleva a `/login` (sesión caducada: no se ha guardado nada) ni la que
     * lleva al reto de Anubis (la petición ni siquiera llegó a BookWyrm). Antes cualquier 302
     * se daba por éxito y la app decía «hecho» sin haber hecho nada.
     */
    fun isOkRedirect(response: retrofit2.Response<*>): Boolean {
        if (response.code() !in 300..399) return false
        val location = response.headers()["Location"]
        if (com.ferlagod.rocinante.data.api.NetworkClient.isLoginLocation(location, response.raw().request.url)) return false
        if (location?.contains(com.ferlagod.rocinante.data.api.AnubisClearance.CHALLENGE_PATH) == true) return false
        return true
    }

    /** ¿Ha guardado la instancia lo enviado? Un 2xx o una redirección buena ([isOkRedirect]). */
    fun isWriteSuccess(response: retrofit2.Response<*>): Boolean =
        response.isSuccessful || isOkRedirect(response)

    /**
     * Cierra el cuerpo de una respuesta que no se va a leer. Retrofit deja abierta la conexión
     * hasta que alguien lo hace, y las que se quedaban sin cerrar agotaban el pool.
     */
    fun closeQuietly(response: retrofit2.Response<*>?) {
        if (response == null) return
        try { (response.body() as? java.io.Closeable)?.close() } catch (_: Exception) {}
        try { response.errorBody()?.close() } catch (_: Exception) {}
    }

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

    /**
     * La página de una cita tal y como la escribe quien la copia: «23», «23-25», «23 – 25».
     *
     * @return la primera página y, si es un tramo, la última; null si no son números, y entonces
     *   quien llama la deja en el texto como antes.
     */
    fun parseQuotePages(raw: String?): Pair<Int, Int?>? {
        val match = Regex("""^\s*(\d{1,6})\s*(?:[-–—]\s*(\d{1,6})\s*)?$""").find(raw.orEmpty())
            ?: return null
        val first = match.groupValues[1].toInt()
        val last = match.groupValues[2].toIntOrNull()?.takeIf { it > first }
        return first to last
    }

    /**
     * Convierte la fecha que BookWyrm pinta en los estados («Aug. 22, 2024», «22 de agosto», «hace
     * 3 horas») en una [java.time.LocalDate] comparable, para ordenar las reseñas.
     *
     * Viene en el idioma de quien mira: antes solo se entendía el inglés, y en cualquier otro las
     * reseñas quedaban sin ordenar. BookWyrm escribe la fecha completa si es de otro año, sin año
     * si es de este, y una frase relativa («hace 3 horas», «now») si es de hoy.
     *
     * @param raw Texto de fecha extraído del HTML, o una fecha ISO.
     * @param language El `lang` de la página, si se sabe; sin él se busca el mes en todos.
     * @return La fecha, o null si no se reconoce.
     */
    fun parseReviewDate(
        raw: String?,
        language: String? = null,
        today: java.time.LocalDate = java.time.LocalDate.now()
    ): java.time.LocalDate? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        // Caso ISO 8601 (el atributo datetime, si lo hay).
        if (s.length >= 10 && s[4] == '-' && s[7] == '-') {
            try {
                return java.time.LocalDate.parse(s.substring(0, 10))
            } catch (_: Exception) { /* continuar con el parseo legible */ }
        }

        ShelvedDateParser.parse(s, language, today, defaultYear = today.year)?.let { return it }
        // Sin año ni mes reconocibles solo queda la frase relativa de las últimas horas.
        return if (Regex("""\d{4}""").containsMatchIn(s)) null else today
    }
}