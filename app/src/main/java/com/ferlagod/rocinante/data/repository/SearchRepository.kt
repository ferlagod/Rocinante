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
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.BookSearchResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class SearchRepository(
    private val api: BookWyrmApi
) {
    /**
     * Busca libros en la instancia, como la página /search de la web.
     *
     * BookWyrm enseña primero lo que tiene en su base de datos. Los catálogos externos que
     * tenga configurados el administrador (Open Library, Inventaire, otras instancias...) solo
     * los consulta si no hay nada local o si se le pide con `remote=true`, que es lo que hace
     * el enlace «Cargar resultados de otros catálogos» de la web.
     *
     * @param remote Pedir también los catálogos externos. Es lento: la instancia los consulta
     *   en ese momento, uno detrás de otro.
     */
    suspend fun searchBooksScraped(query: String, instanceUrl: String, remote: Boolean = false): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"

        try {
            val response = api.searchBooks(query, remote = if (remote) "true" else null)
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body()?.string() ?: return@withContext emptyList()

            // Intento JSON primero por si es una API moderna pura
            try {
                if (responseBody.trimStart().startsWith("[")) {
                    val typeToken = object : com.google.gson.reflect.TypeToken<List<BookSearchResult>>() {}.type
                    return@withContext Gson().fromJson(responseBody, typeToken)
                }
            } catch (_: Exception) {}

            parseBookSearchPage(responseBody, baseUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    companion object {
        private val YEAR_REGEX = "\\b(1[5-9]|20)\\d{2}\\b".toRegex()

        /**
         * Lee la página de resultados de /search (search/book.html).
         *
         * - Locales: `li.local-book-search-result`, con el título y el autor enlazando a
         *   /book/<id> y /author/<id> de la propia instancia.
         * - Remotos: un `section` por catálogo, con su nombre en el `summary` y cada libro en
         *   un `li` con un formulario a /resolve-book que lleva el `remote_id`. El título enlaza
         *   fuera, a veces a otra instancia de BookWyrm (otra.instancia/book/123), así que esos
         *   enlaces no se pueden tomar por libros de aquí.
         */
        internal fun parseBookSearchPage(html: String, baseUrl: String): List<BookSearchResult> {
            val document = org.jsoup.Jsoup.parse(html, baseUrl)
            val results = mutableListOf<BookSearchResult>()
            val base = baseUrl.trimEnd('/')

            fun absolute(src: String?): String? = when {
                src.isNullOrBlank() -> null
                src.startsWith("http") -> src
                else -> base + "/" + src.trimStart('/')
            }

            fun isRemoteItem(el: org.jsoup.nodes.Element): Boolean =
                el.selectFirst("form[action*=resolve-book]") != null || el.closest("details") != null

            // 1. Locales. Si la instancia no marca la clase (versiones viejas), cualquier li
            // con un enlace a un libro que no sea de un catálogo externo.
            val localElements = document.select("li.local-book-search-result")
                .ifEmpty { document.select("li:has(a[href*=/book/])") }
                .filterNot { isRemoteItem(it) }
            for (element in localElements) {
                val anchor = element.select("a[href*=/book/]").firstOrNull { a ->
                    val href = a.absUrl("href").ifEmpty { a.attr("href") }
                    !href.startsWith("http") || href.startsWith(base)
                } ?: continue
                val key = anchor.attr("href").substringAfter("/book/").substringBefore("/").substringBefore("?")
                if (key.isBlank()) continue
                val author = (element.selectFirst("a.author") ?: element.selectFirst("a[href*=/author/]"))?.text()
                // La ÚLTIMA coincidencia, por si el título es un año («1984»).
                val year = YEAR_REGEX.findAll(element.text()).lastOrNull()?.value?.toIntOrNull()
                results += BookSearchResult(
                    key = key,
                    title = anchor.text(),
                    author = author,
                    year = year,
                    cover = absolute(element.selectFirst("img")?.attr("src")),
                    isRemote = false
                )
            }

            // 2. Catálogos externos, con el nombre de cada uno.
            for (form in document.select("form[action*=resolve-book]")) {
                val remoteId = form.selectFirst("input[name=remote_id]")?.attr("value")?.trim()
                if (remoteId.isNullOrBlank()) continue
                val element = form.closest("li") ?: form.parent() ?: continue
                val source = (form.closest("section") ?: form.closest("details"))
                    ?.selectFirst("summary")
                    ?.let { summary -> summary.selectFirst("a")?.text() ?: summary.text() }
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: runCatching { java.net.URI(remoteId).host?.removePrefix("www.") }.getOrNull()

                val title = element.selectFirst("strong")?.text()?.trim().orEmpty()
                // Debajo del título va «Autor (año)», sin traducir.
                val byline = element.select("p")
                    .firstOrNull { it.selectFirst("strong") == null && it.selectFirst("form") == null && it.text().isNotBlank() }
                    ?.text()?.trim().orEmpty()
                val yearMatch = Regex("""\((\d{4})\)\s*$""").find(byline)
                val author = (if (yearMatch != null) byline.substring(0, yearMatch.range.first) else byline)
                    .trim().takeIf { it.isNotEmpty() }

                results += BookSearchResult(
                    key = remoteId,
                    title = title.ifEmpty { null },
                    author = author,
                    year = yearMatch?.groupValues?.get(1)?.toIntOrNull(),
                    cover = absolute(element.selectFirst("img")?.attr("src")),
                    isRemote = true,
                    remoteId = remoteId,
                    source = source
                )
            }

            return results.distinctBy { it.key }
        }
    }

    /**
     * Busca una edición por ISBN exacto contra /isbn/<isbn>.json, un endpoint JSON
     * estable de BookWyrm. Solo consulta la base de datos LOCAL de la instancia; si la
     * obra aún no existe localmente devuelve una lista vacía, y quien llama debe recurrir
     * a [searchBooksScraped] (que sí encuentra ediciones remotas vía conectores).
     *
     * @return resultados locales exactos, o lista vacía si no hay coincidencia local.
     */
    suspend fun searchByIsbn(isbn: String): List<BookSearchResult> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchByIsbn(isbn)
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body()?.string()?.trimStart() ?: return@withContext emptyList()
            // El endpoint devuelve un array JSON de resultados (title, key, author, year, cover).
            if (!body.startsWith("[")) return@withContext emptyList()

            val typeToken = object : com.google.gson.reflect.TypeToken<List<BookSearchResult>>() {}.type
            Gson().fromJson<List<BookSearchResult>>(body, typeToken)?.filter { !it.key.isNullOrBlank() } ?: emptyList()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    /**
     * Realiza una búsqueda de usuarios devolviendo el HTML y raspando los resultados.
     */
    suspend fun searchUsersScraped(query: String, instanceUrl: String): List<com.ferlagod.rocinante.data.model.SuggestedUser> = withContext(Dispatchers.IO) {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        
        try {
            // Realizar búsqueda pasando type=user
            val searchUrl = "${baseUrl}search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=user"
            
            val response = api.getRawHtmlResponse(searchUrl)
            if (!response.isSuccessful) return@withContext emptyList()
            
            val responseBody = response.body()?.string() ?: return@withContext emptyList()
            
            val document = org.jsoup.Jsoup.parse(responseBody)
            val results = mutableListOf<com.ferlagod.rocinante.data.model.SuggestedUser>()
            
            val mediaElements = document.select(".media")
            for (element in mediaElements) {
                // Seleccionar el primer enlace útil (avatar o enlace con /user/)
                val userAnchor = element.selectFirst("a.media-left") ?: element.selectFirst("a[href*=/user/]") ?: element.selectFirst("a") ?: continue
                val profileUrl = userAnchor.attr("href")
                
                // Excluir links internos como /books o /followers
                if (profileUrl.endsWith("/books") || profileUrl.endsWith("/followers") || profileUrl.endsWith("/following")) {
                    continue
                }
                
                val avatarImg = element.selectFirst("img.avatar") ?: element.selectFirst("img")
                val avatarRaw = avatarImg?.attr("src")
                val avatarUrl = if (avatarRaw != null && !avatarRaw.startsWith("http")) {
                    baseUrl.trimEnd('/') + "/" + avatarRaw.trimStart('/')
                } else avatarRaw ?: ""
                
                val nameElement = element.selectFirst(".title")
                val name = nameElement?.text()?.replace("Blocca account", "")?.trim() ?: ""
                
                val handleElement = element.selectFirst(".subtitle")
                val handle = handleElement?.text()?.trim() ?: profileUrl.substringAfter("/user/")
                
                results.add(
                    com.ferlagod.rocinante.data.model.SuggestedUser(
                        name = name,
                        handle = handle,
                        avatarUrl = avatarUrl,
                        profileUrl = if (profileUrl.startsWith("http")) profileUrl else baseUrl.trimEnd('/') + "/" + profileUrl.trimStart('/')
                    )
                )
            }
            results.distinctBy { it.handle }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }
}
