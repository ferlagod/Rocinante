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
package com.ferlagod.rocinante.data.api

import com.ferlagod.rocinante.utils.IsbnUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Datos de un libro por su ISBN en OpenLibrary, para rellenar el alta manual.
 *
 * Se consulta solo cuando el usuario lo pide, y con el cliente de imágenes: sigue redirecciones
 * y, al no ser la instancia, no lleva sus cookies ni su token CSRF.
 */
object OpenLibraryLookup {

    /**
     * Lo que se sabe del libro. Cualquier campo puede faltar.
     *
     * @property publishedDate Tal cual la da OpenLibrary («2003», «May 2003», «2003-05-01»…);
     *   [isoDateOrYear] la deja en un formato que BookWyrm acepte.
     */
    data class BookData(
        val title: String = "",
        val subtitle: String = "",
        val authors: List<String> = emptyList(),
        val pages: Int? = null,
        val publishedDate: String = "",
        val publishers: List<String> = emptyList(),
        val coverUrl: String? = null
    )

    /** Busca el ISBN en OpenLibrary. null si no hay datos o no se pudo consultar. */
    suspend fun lookup(isbn: String): BookData? = withContext(Dispatchers.IO) {
        val clean = IsbnUtils.normalize(isbn)
        if (!IsbnUtils.isIsbn(clean)) return@withContext null
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$clean&format=json&jscmd=data"
        try {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            NetworkClient.imageCallFactory.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(response.body?.string().orEmpty(), clean)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** Descarga la portada. null si no hay o no se pudo. */
    suspend fun downloadCover(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            NetworkClient.imageCallFactory.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val type = response.body?.contentType()
                if (type != null && type.type != "image") return@withContext null
                response.body?.bytes()?.takeIf { it.size > 1024 }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** Lee la respuesta de /api/books?jscmd=data. */
    internal fun parse(json: String, isbn: String): BookData? {
        return try {
            @Suppress("DEPRECATION")
            val root = com.google.gson.JsonParser().parse(json)
            if (!root.isJsonObject) return null
            val entry = root.asJsonObject.get("ISBN:$isbn")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return null

            fun str(name: String): String =
                entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

            fun names(name: String): List<String> =
                entry.getAsJsonArray(name)?.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject?.get("name")
                        ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                }.orEmpty()

            val cover = entry.getAsJsonObject("cover")?.let { c ->
                listOf("large", "medium").firstNotNullOfOrNull { size ->
                    c.get(size)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.startsWith("http") }
                }
            }

            BookData(
                title = str("title"),
                subtitle = str("subtitle"),
                authors = names("authors").distinct(),
                pages = entry.get("number_of_pages")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()?.takeIf { it > 0 },
                publishedDate = str("publish_date"),
                publishers = names("publishers").distinct(),
                coverUrl = cover
            ).takeIf { it.title.isNotEmpty() || it.authors.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * La fecha en un formato que BookWyrm acepte: «AAAA-MM-DD» si viene completa en ISO, o solo
     * el año si es lo único que se puede sacar con seguridad («May 2003» → «2003»). Vacía si no
     * hay año.
     */
    internal fun isoDateOrYear(raw: String): String {
        val text = raw.trim()
        Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(text)?.let { return it.value }
        return Regex("""\b(1[5-9]\d{2}|20\d{2})\b""").find(text)?.value.orEmpty()
    }
}
