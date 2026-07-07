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

import com.ferlagod.rocinante.data.model.*

import com.ferlagod.rocinante.utils.HtmlUtils
import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.ActivityPubObject
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.BookSearchResult
import com.ferlagod.rocinante.data.model.TimelineUiItem
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers


class SearchRepository(
    private val api: BookWyrmApi
) {
    /**
     * Realiza una búsqueda devolviendo el HTML y raspando los resultados.
     * Soporta tanto resultados locales como remotos (conectores).
     */
    suspend fun searchBooksScraped(query: String, instanceUrl: String): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        
        try {
            val response = api.searchBooks(query)
            if (!response.isSuccessful) return@withContext emptyList()
            
            val responseBody = response.body()?.string() ?: return@withContext emptyList()
            
            // Intento JSON primero por si es una API moderna pura
            try {
                if (responseBody.trimStart().startsWith("[")) {
                    val typeToken = object : com.google.gson.reflect.TypeToken<List<BookSearchResult>>() {}.type
                    return@withContext Gson().fromJson(responseBody, typeToken)
                }
            } catch (_: Exception) {}
            
            // Scraping HTML
            val document = org.jsoup.Jsoup.parse(responseBody)
            val results = mutableListOf<BookSearchResult>()
            
            // 1. Extraer resultados locales
            val localElements = document.select("li.local-book-search-result, li:has(a[href*=/book/])")
            for (element in localElements) {
                // Ignore resolve-book forms in the local pass
                if (element.selectFirst("form[action*=/resolve-book/]") != null) continue

                val anchor = element.selectFirst("a[href*=/book/]") ?: continue
                val title = anchor.text()
                val key = anchor.attr("href").substringAfter("/book/").substringBefore("/")
                
                val authorAnchor = element.selectFirst("a.author") ?: element.selectFirst("a[href*=/author/]")
                val author = authorAnchor?.text()
                
                val img = element.selectFirst("img.book-cover")
                val coverRaw = img?.attr("src")
                val cover = if (coverRaw != null && !coverRaw.startsWith("http")) {
                    baseUrl.trimEnd('/') + coverRaw
                } else coverRaw
                
                // Intentar extraer el año (ej: "(pubblicato 1949)" o "(published 1949)")
                val textContent = element.text()
                val yearMatch = "\\b(18|19|20)\\d{2}\\b".toRegex().find(textContent)
                val year = yearMatch?.value?.toIntOrNull()
                
                if (key.isNotBlank()) {
                    results.add(
                        BookSearchResult(
                            key = key,
                            title = title,
                            author = author,
                            year = year,
                            cover = cover,
                            isRemote = false
                        )
                    )
                }
            }
            
            // 2. Extraer resultados remotos (conectores federados)
            // BookWyrm usa formularios con action "/resolve-book/" y un campo remote_id oculto
            val remoteElements = document.select("li.remote-book-search-result, li:has(form[action*=/resolve-book/])")
            for (element in remoteElements) {
                // Buscamos el formulario para obtener el remote_id
                val form = element.selectFirst("form[action*=/resolve-book/]")
                val remoteIdInput = form?.selectFirst("input[name=remote_id]")
                val remoteId = remoteIdInput?.attr("value")
                
                if (remoteId.isNullOrBlank()) continue
                
                // Normalmente el título está en negrita o en un span (BookWyrm UI)
                val strongElems = element.select("strong")
                val title = if (strongElems.isNotEmpty()) strongElems.first()?.text() ?: "Libro remoto" else "Libro remoto"
                
                // Extraer autor si está disponible
                val authorText = element.text()
                val author = authorText.substringAfter(" by ", "").substringBefore("(").trim().takeIf { it.isNotBlank() }
                             ?: authorText.substringAfter(" di ", "").substringBefore("(").trim().takeIf { it.isNotBlank() }
                
                val img = element.selectFirst("img.book-cover")
                val cover = img?.attr("src")
                
                val yearMatch = "\\b(18|19|20)\\d{2}\\b".toRegex().find(authorText)
                val year = yearMatch?.value?.toIntOrNull()
                
                results.add(
                    BookSearchResult(
                        key = remoteId, // Guardamos el remoteId en 'key' para que funcione como identificador temporal
                        title = title,
                        author = author,
                        year = year,
                        cover = cover,
                        isRemote = true,
                        remoteId = remoteId
                    )
                )
            }
            
            // Evitar duplicados basados en 'key'
            results.distinctBy { it.key }
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
                val userAnchor = element.selectFirst("a[href*=/user/]") ?: continue
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
