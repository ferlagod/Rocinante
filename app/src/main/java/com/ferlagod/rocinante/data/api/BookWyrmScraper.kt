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

import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.ActivityPubObject
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import com.ferlagod.rocinante.data.model.NotificationType
import com.ferlagod.rocinante.data.model.NotificationUiItem
import com.ferlagod.rocinante.data.model.SuggestedUser
import com.ferlagod.rocinante.utils.BookWyrmUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody

object BookWyrmScraper {

    /** Etiqueta de logcat para diagnosticar el borrado de notificaciones. */
    private const val TAG_CLEAR = "RocinanteNotif"

    /**
     * Versión del formato de [com.ferlagod.rocinante.data.model.BookEnrichment].
     * Súbela al empezar a extraer campos nuevos de la página del libro: las entradas
     * cacheadas con una versión anterior se vuelven a leer una sola vez.
     * 2 → añade los identificadores para quitar el libro de su estantería.
     */
    const val ENRICHMENT_SCHEMA_VERSION = 2

    /**
     * Contexto temporal utilizado al actualizar el progreso de lectura.
     */
    data class ProgressContext(
        val readthroughId: String,
        val userId: String,
        val localBookId: String,
        // Token CSRF (masked) extraído del render de la página del libro.
        // BookWyrm (Django) valida este token de formulario contra la cookie csrftoken;
        // enviarlo como campo evita el 403 en instancias donde la cabecera X-CSRFToken
        // no es suficiente. Puede quedar vacío si no se encuentra en el HTML.
        val csrfToken: String = "",
        val startDate: String? = null
    )
    /**
     * Contexto temporal utilizado al vincular un usuario y un libro en una reseña.
     */
    data class ReviewContext(val userId: String, val bookId: String)

    /**
     * Extrae el user ID y el book ID ocultos en el HTML de la página del libro para enviar reseñas.
     */
    suspend fun getReviewContext(api: BookWyrmApi, bookUrl: String): ReviewContext? = withContext(Dispatchers.IO) {
        try {
            val bookId = BookWyrmUtils.extractBookId(bookUrl)
            if (bookId.isEmpty()) return@withContext null
            val hostUrl = java.net.URL(bookUrl).let { "${it.protocol}://${it.host}" }
            var currentUrl = "$hostUrl/book/$bookId"
            var html = ""
            for (i in 0..3) {
                val response = api.getRawHtmlResponse(currentUrl)
                if (response.isSuccessful) {
                    html = response.body()?.string() ?: ""
                    break
                } else if (response.code() in 300..399) {
                    val location = response.headers()["Location"]
                    if (location != null) {
                        currentUrl = if (location.startsWith("http")) location else {
                            val hostUrlObj = java.net.URL(bookUrl).let { "${it.protocol}://${it.host}" }
                            "$hostUrlObj$location"
                        }
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            val userId = extractHiddenFieldValue(html, "user")
            val formBookId = extractHiddenFieldValue(html, "book") ?: bookId

            if (userId != null) {
                ReviewContext(userId, formBookId)
            } else null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Resuelve la URL del actor de una reseña a su handle de seguimiento (@usuario@instancia).
     */
    suspend fun resolveActorHandle(api: BookWyrmApi, actorUrl: String, instanceHostUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            if (actorUrl.isBlank()) return@withContext null
            val absolute = if (actorUrl.startsWith("http")) actorUrl
                           else instanceHostUrl.trimEnd('/') + "/" + actorUrl.trimStart('/')
            val raw = api.getRawJson(absolute).string()
            if (!raw.trimStart().startsWith("{")) return@withContext null
            val profile = com.google.gson.Gson().fromJson(raw, BookWyrmProfile::class.java)
            val preferredUsername = profile.preferredUsername
                ?: profile.id?.substringAfterLast("/")
                ?: return@withContext null
            val host = try {
                java.net.URI(profile.id ?: absolute).host ?: ""
            } catch (_: Exception) { "" }
            if (host.isNotEmpty()) "@$preferredUsername@$host" else "@$preferredUsername"
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Resuelve un libro federado a su URL local siguiendo la redirección de resolve-book.
     */
    suspend fun resolveLocalBookUrl(api: BookWyrmApi, bookUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = api.resolveBook(bookUrl)
                if (response.code() in 300..399) {
                    val location = response.headers()["Location"]
                    if (location != null) {
                        if (location.startsWith("http")) location else {
                            val hostUrl = java.net.URL(response.raw().request.url.toString()).let { "${it.protocol}://${it.host}" }
                            "$hostUrl$location"
                        }
                    } else bookUrl
                } else bookUrl
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                bookUrl
            }
        }
    }

    /**
     * Extrae el token CSRF (csrfmiddlewaretoken) del HTML de una página renderizada.
     * Django puede emitir los atributos en cualquier orden (name antes o después de value),
     * así que se intentan ambos patrones. Devuelve "" si no se encuentra.
     */
    private fun extractCsrfToken(html: String): String {
        return extractHiddenFieldValue(html, "csrfmiddlewaretoken") ?: ""
    }

    /**
     * Extrae el valor de un campo oculto (`<input name="..." value="...">`)
     * del HTML de una página renderizada. Django puede emitir los atributos en
     * cualquier orden (name antes o después de value), así que se intentan
     * ambos patrones. Devuelve null si no se encuentra.
     */
    private fun extractHiddenFieldValue(html: String, fieldName: String): String? {
        // Patrón 1: name antes de value  →  name="fieldName" ... value="XXX"
        val nameFirst = """name=["']${Regex.escape(fieldName)}["'][^>]*?value=["']([^"']+)["']""".toRegex()
        nameFirst.find(html)?.groupValues?.get(1)?.let { return it }
        // Patrón 2: value antes de name  →  value="XXX" ... name="fieldName"
        val valueFirst = """value=["']([^"']+)["'][^>]*?name=["']${Regex.escape(fieldName)}["']""".toRegex()
        valueFirst.find(html)?.groupValues?.get(1)?.let { return it }
        return null
    }

    /**
     * Extrae el readthrough ID del formulario de progreso de lectura.
     * Busca el `<form name="reading-progress-...">` y dentro de él, el input
     * `<input name="id" value="(\d+)">` en cualquier orden de atributos.
     */
    private fun extractReadthroughId(html: String): String? {
        // Localizar el bloque del formulario de progreso
        val formRegex = """<form[^>]*name=["']reading-progress-[\s\S]*?</form>""".toRegex()
        val formBlock = formRegex.find(html)?.value ?: return null
        // Dentro del formulario, buscar el campo "id" en ambos órdenes
        return extractHiddenFieldValue(formBlock, "id")
    }

    /**
     * Obtiene el contexto necesario para actualizar el progreso (readthrough ID y user ID).
     */
    suspend fun getProgressContext(api: BookWyrmApi, bookUrl: String): ProgressContext? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val localUrl = resolveLocalBookUrl(api, bookUrl) ?: bookUrl
                val localBookId = BookWyrmUtils.extractBookId(localUrl)

                val response = api.getRawHtmlResponse(localUrl)
                if (!response.isSuccessful) {
                    var currentUrl = localUrl
                    var html = ""
                    for (i in 0..3) {
                        val redirResponse = api.getRawHtmlResponse(currentUrl)
                        if (redirResponse.isSuccessful) {
                            html = redirResponse.body()?.string() ?: ""
                            break
                        } else if (redirResponse.code() in 300..399) {
                            val location = redirResponse.headers()["Location"]
                            if (location != null) {
                                currentUrl = if (location.startsWith("http")) location else {
                                    val hostUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}" }
                                    "$hostUrl$location"
                                }
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }
                    if (html.isEmpty()) return@withContext null
                    
                    val readthroughId = extractReadthroughId(html)
                    val userId = extractHiddenFieldValue(html, "user")
                    val startDate = extractHiddenFieldValue(html, "start_date")?.takeIf { it.isNotBlank() }
                    val formBookId = extractHiddenFieldValue(html, "book") ?: localBookId

                    if (readthroughId != null && userId != null) {
                        return@withContext ProgressContext(readthroughId, userId, formBookId, extractCsrfToken(html), startDate)
                    } else {
                        return@withContext null
                    }
                }

                val html = response.body()?.string() ?: return@withContext null

                val readthroughId = extractReadthroughId(html)
                val userId = extractHiddenFieldValue(html, "user")
                val startDate = extractHiddenFieldValue(html, "start_date")?.takeIf { it.isNotBlank() }
                val formBookId = extractHiddenFieldValue(html, "book") ?: localBookId

                if (readthroughId != null && userId != null) {
                    ProgressContext(readthroughId, userId, formBookId, extractCsrfToken(html), startDate)
                } else null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    /**
     * Progreso de lectura actual de un libro para el usuario logueado.
     * @property progress Valor numérico (páginas si mode == "PG", porcentaje si "PCT").
     * @property mode "PG" (páginas) o "PCT" (porcentaje).
     */
    data class ReadingProgressInfo(
        val progress: Int,
        val mode: String
    )

    /**
     * Obtiene el progreso de lectura actual leyendo el formulario de actualización
     * (reading-progress-…) que BookWyrm renderiza pre-rellenado con readthrough.progress.
     * Devuelve null si el libro no está en lectura o no se encuentra el progreso.
     */
    suspend fun getReadingProgress(api: BookWyrmApi, bookUrl: String): ReadingProgressInfo? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val localUrl = resolveLocalBookUrl(api, bookUrl) ?: bookUrl
                val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
                val html = fetchHtmlWithRedirects(api, localUrl, baseUrl)
                if (html.isEmpty()) return@withContext null
                parseReadingProgress(html)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    /**
     * Extrae progreso y modo del bloque <form name="reading-progress-…">.
     * El input de progreso se acota a su propia etiqueta para no confundirlo
     * con el campo oculto "id" (readthrough) cuando el progreso está vacío.
     */
    private fun parseReadingProgress(html: String): ReadingProgressInfo? {
        val formBlock = """<form[^>]*name=["']reading-progress-[\s\S]*?</form>"""
            .toRegex().find(html)?.value ?: return null
        val inputTag = """<input[^>]*name=["']progress["'][^>]*>"""
            .toRegex().find(formBlock)?.value ?: return null
        val progress = """value=["'](\d+)["']"""
            .toRegex().find(inputTag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val isPct = """value=["']PCT["']\s*selected""".toRegex().containsMatchIn(formBlock)
        return ReadingProgressInfo(progress, if (isPct) "PCT" else "PG")
    }

    /**
     * Raspa de la página HTML del libro los datos que NO están en el .json de la estantería:
     * nombre del autor, valoración del usuario (admite medias estrellas) y fechas de lectura
     * en ISO. Reutiliza la misma descarga de página que [getProgressContext]/[getReadingProgress].
     *
     * @param bookUrl id/URL del libro (se usa tal cual como clave de caché).
     * @return [com.ferlagod.rocinante.data.model.BookEnrichment] o null si la página no se pudo leer.
     */
    suspend fun scrapeBookEnrichment(
        api: BookWyrmApi,
        bookUrl: String
    ): com.ferlagod.rocinante.data.model.BookEnrichment? = withContext(Dispatchers.IO) {
        try {
            val localUrl = resolveLocalBookUrl(api, bookUrl) ?: bookUrl
            val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
            val html = fetchHtmlWithRedirects(api, localUrl, baseUrl)
            if (html.isEmpty()) return@withContext null
            val doc = org.jsoup.Jsoup.parse(html)

            // Autor: microdatos schema.org en el subtítulo; reserva a la meta DC.Creator del <head>.
            val authorName = doc.select("a.author span[itemprop=name]")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifEmpty {
                    doc.select("meta[name=DC.Creator]")
                        .mapNotNull { it.attr("content").trim().ifEmpty { null } }
                        .distinct()
                        .joinToString(", ")
                }
                .ifEmpty { null }

            // Valoración del usuario: el formulario "rate" pre-marca (checked) todos los radios
            // hasta el valor actual; la valoración = máximo valor marcado (admite medias estrellas).
            val rating = doc.select("form[name=rate] input[name=rating]")
                .filter { it.hasAttr("checked") }
                .mapNotNull { it.attr("value").toDoubleOrNull() }
                .maxOrNull()

            // Fechas de lectura en ISO (yyyy-MM-dd) desde los inputs date de los modales
            // edit_readthrough_*; con varias lecturas se toma la fin más reciente y el inicio más antiguo.
            val finished = doc.select("[id^=edit_readthrough_] input[name=finish_date]")
                .mapNotNull { it.attr("value").trim().ifEmpty { null } }
                .maxOrNull()
            val started = doc.select("[id^=edit_readthrough_] input[name=start_date]")
                .mapNotNull { it.attr("value").trim().ifEmpty { null } }
                .minOrNull()

            // Idioma legible (microdatos schema.org), p. ej. "Danish".
            val language = doc.selectFirst("meta[itemprop=inLanguage]")
                ?.attr("content")?.trim()?.ifEmpty { null }

            // Formulario oculto para quitar el libro de su estantería. Solo se renderiza
            // cuando el libro está en una, así que su ausencia deja ambos campos a null.
            val unshelveForm = doc.selectFirst("form[name^=unshelve-]")
            val shelfBookId = unshelveForm?.selectFirst("input[name=book]")
                ?.attr("value")?.trim()?.ifEmpty { null }
            val shelfId = unshelveForm?.selectFirst("input[name=shelf]")
                ?.attr("value")?.trim()?.ifEmpty { null }

            com.ferlagod.rocinante.data.model.BookEnrichment(
                bookId = bookUrl,
                authorName = authorName,
                rating = rating,
                finished = finished,
                started = started,
                language = language,
                shelfBookId = shelfBookId,
                shelfId = shelfId,
                schemaVersion = ENRICHMENT_SCHEMA_VERSION,
                fetchedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Una lectura (readthrough) ya registrada en la página del libro.
     * Las fechas vienen en ISO (yyyy-MM-dd) y cualquiera de las dos puede faltar.
     */
    data class Readthrough(
        val id: String,
        val startDate: String?,
        val finishDate: String?
    )

    /**
     * Todo lo necesario para poner o cambiar las fechas de lectura de un libro: las lecturas
     * que ya tiene (con su id, para editarlas) y los identificadores ocultos que espera el
     * formulario «Add read dates» de BookWyrm.
     */
    data class ReadDatesContext(
        val bookId: String,
        val userId: String,
        val csrfToken: String,
        val readthroughs: List<Readthrough>
    )

    /** Lo que la página del libro cuenta de sus fechas de lectura. */
    data class ParsedReadDates(
        val bookId: String?,
        val userId: String?,
        val readthroughs: List<Readthrough>
    )

    /**
     * Extrae del HTML de la página del libro las lecturas registradas y los identificadores
     * del formulario. BookWyrm dibuja un modal de edición por lectura, con el id oculto y
     * las fechas ya puestas, y siempre uno de alta con el libro y el usuario, haya lecturas
     * o no. Los modales se buscan por la acción de su formulario porque el id del modal lo
     * comparten también trozos suyos que no llevan las fechas (p. ej. la cabecera).
     */
    fun parseReadDates(html: String): ParsedReadDates {
        val doc = org.jsoup.Jsoup.parse(html)

        val editForms = doc.select("form[action\$=edit-readthrough]")
            .ifEmpty { doc.select("[id^=edit_readthrough_]:has(input[name=start_date])") }
        val readthroughs = editForms
            .mapNotNull { form ->
                val id = form.selectFirst("input[name=id]")
                    ?.attr("value")?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                Readthrough(
                    id = id,
                    startDate = form.selectFirst("input[name=start_date]")
                        ?.attr("value")?.trim()?.ifEmpty { null },
                    finishDate = form.selectFirst("input[name=finish_date]")
                        ?.attr("value")?.trim()?.ifEmpty { null }
                )
            }
            .distinctBy { it.id }

        val addForm = doc.selectFirst("form[action\$=create-readthrough]")
            ?: doc.selectFirst("#add-readthrough")
        val bookId = addForm?.selectFirst("input[name=book]")?.attr("value")?.trim()?.ifEmpty { null }
            ?: extractHiddenFieldValue(html, "book")
        val userId = addForm?.selectFirst("input[name=user]")?.attr("value")?.trim()?.ifEmpty { null }
            ?: extractHiddenFieldValue(html, "user")

        return ParsedReadDates(bookId, userId, readthroughs)
    }

    /**
     * Descarga la página del libro y devuelve lo necesario para poner o cambiar sus fechas.
     *
     * @return null si la página no se pudo leer o no trae el formulario (sesión caducada).
     */
    suspend fun getReadDatesContext(
        api: BookWyrmApi,
        bookUrl: String
    ): ReadDatesContext? = withContext(Dispatchers.IO) {
        try {
            val localUrl = resolveLocalBookUrl(api, bookUrl) ?: bookUrl
            val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
            val html = fetchHtmlWithRedirects(api, localUrl, baseUrl)
            if (html.isEmpty()) return@withContext null

            val parsed = parseReadDates(html)
            // Sin el id del libro en el formulario queda el de la propia URL; sin usuario,
            // en cambio, no hay forma de dar de alta una lectura nueva.
            val bookId = parsed.bookId
                ?: BookWyrmUtils.extractBookId(localUrl).takeIf { it.isNotEmpty() }
                ?: return@withContext null
            val userId = parsed.userId ?: return@withContext null

            // Token sin enmascarar de la cookie: es el que Django compara siempre bien.
            // Si el jar aún no lo tiene, sirve el del propio formulario de la página.
            val csrfToken = NetworkClient.currentCsrfToken() ?: extractCsrfToken(html)

            ReadDatesContext(bookId, userId, csrfToken, parsed.readthroughs)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun scrapeHomeFeed(
        api: BookWyrmApi,
        instanceUrl: String,
        maxPages: Int = 2
    ): List<com.ferlagod.rocinante.data.model.TimelineUiItem> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<com.ferlagod.rocinante.data.model.TimelineUiItem>()
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"

        for (page in 1..maxPages) {
            try {
                val feedUrl = if (page == 1) baseUrl else "${baseUrl}?page=$page"
                val html = fetchHtmlWithRedirects(api, feedUrl, baseUrl)
                if (html.isEmpty()) break

                val document = org.jsoup.Jsoup.parse(html)
                val pageItems = parseStatusCards(document, baseUrl)

                if (pageItems.isEmpty()) break
                allItems.addAll(pageItems)
            } catch (_: Exception) {
                break
            }
        }

        allItems.distinctBy { it.id }
    }

    suspend fun fetchHtmlWithRedirects(
        api: BookWyrmApi,
        url: String,
        baseUrl: String
    ): String {
        var currentUrl = url
        for (i in 0..3) {
            val response = api.getRawHtmlResponse(currentUrl)
            if (response.isSuccessful) {
                return response.body()?.string() ?: ""
            } else if (response.code() in 300..399) {
                val location = response.headers()["Location"]
                if (location != null) {
                    currentUrl = if (location.startsWith("http")) location else {
                        baseUrl.trimEnd('/') + location
                    }
                } else break
            } else break
        }
        return ""
    }

    private fun parseStatusCards(
        document: org.jsoup.nodes.Document,
        baseUrl: String
    ): List<com.ferlagod.rocinante.data.model.TimelineUiItem> {
        val items = mutableListOf<com.ferlagod.rocinante.data.model.TimelineUiItem>()
        val cleanBase = baseUrl.trimEnd('/')

        var elements = document.select("#feed > .block, .column.is-two-thirds > .block")
        
        if (elements.isEmpty()) {
            val statusElements = document.select(
                "[data-date], .block[data-id], .is-flex.is-align-items-stretch"
            )
            elements = if (statusElements.isEmpty()) document.select(".block:has(time):has(img)") else statusElements
        }

        for (element in elements) {
            try {
                val timeElement = element.selectFirst("time")
                val breadcrumbDate = element.selectFirst(".breadcrumb li a")?.text() ?: ""
                val publishedDate = timeElement?.attr("datetime")?.takeIf { it.isNotBlank() } 
                    ?: timeElement?.text()?.takeIf { it.isNotBlank() } 
                    ?: breadcrumbDate



                val permalinkElement = element.select("a").firstOrNull { a ->
                    val href = a.attr("href")
                    href.contains("/status/") || href.contains("/review/") ||
                    href.contains("/comment/") || href.contains("/quotation/") ||
                    href.contains("/reviewrating/")
                }
                val statusId = permalinkElement?.attr("href")?.let { href ->
                    resolveUrl(href, cleanBase)
                } ?: "scraped-${publishedDate.hashCode()}-${items.size}"

                val avatarImg = element.selectFirst(".media-left img")
                    ?: element.selectFirst("img.avatar")
                    ?: element.selectFirst("img[src*=avatar]")
                val avatarSrc = avatarImg?.attr("src") ?: ""
                val avatarUrl = resolveUrl(avatarSrc, cleanBase)

                val actorLink = element.selectFirst("[itemprop=author] a[itemprop=url]")
                    ?: element.selectFirst("[itemprop=author] a")
                    ?: element.selectFirst(".status-info a")
                    ?: element.selectFirst("a[href*='/user/']")
                    
                val actorNameElement = element.selectFirst("[itemprop=author] [itemprop=name]")
                val actorName = actorNameElement?.text()?.trim() 
                    ?: actorLink?.text()?.trim() 
                    ?: ""
                    
                val actorUrl = actorLink?.attr("href")?.let { href ->
                    resolveUrl(href, cleanBase)
                } ?: ""

                val statusType = detectStatusType(element, statusId)

                var rawContent = ""
                if (statusType == "Quotation") {
                    val quoteDiv = element.selectFirst(".quote.block")
                    val quoteElement = quoteDiv?.selectFirst("blockquote") ?: element.selectFirst("blockquote")
                    val quoteText = quoteElement?.text()?.trim() ?: ""
                    
                    val pText = quoteDiv?.selectFirst("p")?.text()?.trim() ?: ""
                    val parenMatches = "\\([^)]*\\d+[^)]*\\)".toRegex().findAll(pText).toList()
                    val pageText = parenMatches.lastOrNull()?.value ?: ""
                    
                    val commentElement = element.selectFirst("[itemprop=reviewBody]")
                        ?: element.selectFirst(".content .e-content")
                        ?: element.selectFirst(".e-content")
                    var commentContent = ""
                    
                    if (commentElement != null) {
                        val commentClone = commentElement.clone()
                        commentClone.select(".quote.block").remove()
                        commentClone.select("blockquote").remove()
                        commentContent = commentClone.text().trim()
                    }
                    
                    if (quoteText.isNotEmpty()) {
                        rawContent = "«$quoteText»"
                        if (pageText.isNotEmpty()) {
                            rawContent += " $pageText"
                        }
                        if (commentContent.isNotEmpty()) {
                            rawContent += " — $commentContent"
                        }
                    } else {
                        rawContent = commentContent
                    }
                    
                    if (rawContent.isBlank()) {
                        val fallbackContent = element.selectFirst("div.content")
                        rawContent = fallbackContent?.text() ?: ""
                    }
                } else {
                    val contentElement = element.selectFirst("[itemprop=reviewBody]")
                        ?: element.selectFirst(".content .quote .e-content")
                        ?: element.selectFirst(".content .e-content")
                        ?: element.selectFirst(".e-content")
                        ?: element.selectFirst("blockquote")
                        ?: element.selectFirst("div.content")
                    rawContent = contentElement?.html() ?: ""
                }

                val reviewTitle = element.selectFirst("h3[itemprop=name]")?.text()
                    ?: element.selectFirst(".review-title")?.text()
                    ?: ""

                val fullContent = if (reviewTitle.isNotEmpty() && rawContent.isNotEmpty()) {
                    "$reviewTitle — $rawContent"
                } else if (reviewTitle.isNotEmpty()) {
                    reviewTitle
                } else {
                    rawContent
                }

                val cleanContent = com.ferlagod.rocinante.utils.HtmlUtils.stripHtml(fullContent)

                val headerText = element.selectFirst(".status-info")?.text()
                    ?: element.selectFirst(".card-header-title")?.text()
                    ?: ""
                val displayContent = if (cleanContent.isBlank() && headerText.isNotBlank()) {
                    com.ferlagod.rocinante.utils.HtmlUtils.stripHtml(headerText)
                } else {
                    cleanContent
                }

                val coverImg = element.selectFirst("img[src*=covers]")
                    ?: element.selectFirst("img[src*=images/covers]")
                    ?: element.selectFirst("img[alt*=cover]")
                    ?: element.selectFirst(".book-cover img")
                    ?: element.selectFirst(".cover-container img")
                    ?: element.selectFirst("img.book-preview-image")
                    ?: element.selectFirst("[itemprop=image] img")
                    ?: element.selectFirst("a[href*='/book/'] img")
                val coverSrc = coverImg?.attr("src")?.ifEmpty { coverImg.attr("data-src") } ?: ""
                val bookCoverUrl = resolveUrl(coverSrc, cleanBase).takeIf { it.isNotEmpty() }

                val bookLink = element.selectFirst("a[href*='/book/']")
                val bookUrl = bookLink?.attr("href")?.let { href ->
                    resolveUrl(href, cleanBase)
                }

                var localId: String? = null
                var isLikedByMe = false
                var isBoostedByMe = false
                
                // Bookwyrm renders BOTH favorite and unfavorite forms. 
                // The active one doesn't have the 'is-hidden' class.
                val anyFavForm = element.selectFirst("form[action*=/favorite/], form[action*=/unfavorite/]")
                if (anyFavForm != null) {
                    val action = anyFavForm.attr("action")
                    val match = """/(favorite|unfavorite)/(\d+)""".toRegex().find(action)
                    if (match != null) {
                        localId = match.groupValues[2]
                    }
                }
                
                val unfavForm = element.selectFirst("form[name=unfavorite]:not(.is-hidden), form[action*=/unfavorite/]:not(.is-hidden)")
                if (unfavForm != null) {
                    isLikedByMe = true
                }

                val unboostForm = element.selectFirst("form[name=unboost]:not(.is-hidden), form[action*=/unboost/]:not(.is-hidden)")
                if (unboostForm != null) {
                    isBoostedByMe = true
                }

                if (localId == null) {
                    val anyBoostForm = element.selectFirst("form[action*=/boost/], form[action*=/unboost/]")
                    if (anyBoostForm != null) {
                        val action = anyBoostForm.attr("action")
                        val match = """/(boost|unboost)/(\d+)""".toRegex().find(action)
                        if (match != null) {
                            localId = match.groupValues[2]
                        }
                    }
                }
                if (localId == null) {
                    val replyPanel = element.selectFirst("[id^=show_comment_]")
                    if (replyPanel != null) {
                        val idAttr = replyPanel.attr("id")
                        val extracted = idAttr.removePrefix("show_comment_")
                        if (extracted.all { it.isDigit() }) {
                            localId = extracted
                        }
                    }
                }

                val item = com.ferlagod.rocinante.data.model.TimelineUiItem(
                    id = statusId,
                    type = statusType,
                    published = publishedDate,
                    content = displayContent,
                    bookCoverUrl = bookCoverUrl,
                    bookUrl = bookUrl,
                    actorName = actorName,
                    actorAvatarUrl = avatarUrl.takeIf { it.isNotEmpty() },
                    objectId = localId ?: statusId,
                    isLikedByMe = isLikedByMe,
                    isBoostedByMe = isBoostedByMe
                )
                
                // Ítem vacío (sin autor, sin texto y sin portada): no aporta nada, se descarta.
                if (item.actorName.isBlank() && item.content.isBlank() && item.bookCoverUrl.isNullOrEmpty()) {
                    continue
                }
                
                items.add(item)
            } catch (_: Exception) {
                continue
            }
        }

        return items
    }

    private fun detectStatusType(element: org.jsoup.nodes.Element, statusId: String): String {
        return when {
            statusId.contains("/review/") -> "Review"
            statusId.contains("/reviewrating/") -> "Review"
            statusId.contains("/comment/") -> "Comment"
            statusId.contains("/quotation/") -> "Quotation"
            element.selectFirst("[itemprop=reviewBody]") != null -> "Review"
            element.selectFirst("[itemprop=ratingValue]") != null -> "Review"
            element.selectFirst("blockquote") != null -> "Quotation"
            element.text().let { text ->
                text.contains("wants to read", ignoreCase = true) ||
                text.contains("quiere leer", ignoreCase = true) ||
                text.contains("started reading", ignoreCase = true) ||
                text.contains("empezó a leer", ignoreCase = true) ||
                text.contains("finished reading", ignoreCase = true) ||
                text.contains("terminó de leer", ignoreCase = true)
            } -> "Add"
            element.text().let { text ->
                text.contains("boosted", ignoreCase = true) ||
                text.contains("compartió", ignoreCase = true)
            } -> "Announce"
            else -> "Note"
        }
    }

    private fun resolveUrl(src: String, cleanBase: String): String {
        if (src.isEmpty()) return ""
        return if (src.startsWith("http")) src else {
            val base = if (cleanBase.endsWith("/")) cleanBase.dropLast(1) else cleanBase
            val path = if (src.startsWith("/")) src else "/$src"
            "$base$path"
        }
    }

    suspend fun scrapeBookReviews(api: BookWyrmApi, bookUrl: String): List<ActivityPubActivity> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = bookUrl
            var html = ""
            for (i in 0..3) {
                val response = api.getRawHtmlResponse(currentUrl)
                if (response.isSuccessful) {
                    html = response.body()?.string() ?: ""
                    break
                } else if (response.code() in 300..399) {
                    val location = response.headers()["Location"]
                    if (location != null) {
                        currentUrl = if (location.startsWith("http")) location else {
                            val hostUrl = java.net.URL(bookUrl).let { "${it.protocol}://${it.host}" }
                            "$hostUrl$location"
                        }
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            if (html.isEmpty()) return@withContext emptyList()
            val document = org.jsoup.Jsoup.parse(html)

            val reviewsList = mutableListOf<ActivityPubActivity>()
            val statusElements = document.select("#reviews article.card, #reviews .block.is-flex .media")

            for ((index, element) in statusElements.withIndex()) {
                val authorElement = element.selectFirst("[itemprop=author] a") ?: element.selectFirst(".card-header-title a")
                val authorName = element.selectFirst("[itemprop=name]")?.text() ?: authorElement?.text()?.trim() ?: "Unknown"
                val actorUrl = authorElement?.attr("href") ?: ""

                val contentElement = element.selectFirst("[itemprop=reviewBody]") ?: element.selectFirst("div.content")
                val contentText = contentElement?.html() ?: ""

                val titleElement = element.selectFirst("h3[itemprop=name]")
                val titleText = titleElement?.html()?.trim() ?: ""

                val finalContent = if (titleText.isNotEmpty()) {
                    "<strong>$titleText</strong><br><br>$contentText"
                } else {
                    contentText
                }

                val ratingMeta = element.selectFirst("meta[itemprop=ratingValue]")
                val ratingValue = ratingMeta?.attr("content")?.toFloatOrNull()?.toInt()

                val dateElement = element.selectFirst("time")
                var publishedDate = dateElement?.attr("datetime") ?: ""
                if (publishedDate.isEmpty()) {
                    val dateLink = element.select("a").firstOrNull {
                        it.attr("href").contains("/reviewrating/") || it.attr("href").contains("/review/") || it.attr("href").contains("/status/")
                    }
                    if (dateLink != null) {
                        publishedDate = dateLink.text()
                    }
                }

                val avatarElement = element.selectFirst(".media-left img") ?: element.selectFirst("img.avatar") ?: element.selectFirst("img")
                val avatarSrc = avatarElement?.attr("src") ?: ""
                val avatarUrl = if (avatarSrc.isNotEmpty()) {
                    if (avatarSrc.startsWith("http")) avatarSrc else {
                        val hostUrl = java.net.URL(bookUrl).let { "${it.protocol}://${it.host}" }
                        val cleanHost = hostUrl.trimEnd('/')
                        val cleanSrc = avatarSrc.trimStart('/')
                        "$cleanHost/$cleanSrc"
                    }
                } else ""

                val activity = ActivityPubActivity(
                    id = "$bookUrl/review/$index",
                    type = "Review",
                    actor = actorUrl,
                    name = authorName,
                    published = publishedDate,
                    rawObjectData = com.google.gson.Gson().toJsonTree(ActivityPubObject(
                        id = "$bookUrl/review/$index/object",
                        type = "Note",
                        content = finalContent,
                        name = null,
                        rating = ratingValue
                    )),
                    actorAvatarUrl = avatarUrl
                )
                reviewsList.add(activity)
            }

            reviewsList
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSuggestedUsers(api: BookWyrmApi, instanceUrl: String): List<SuggestedUser> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val url = "${baseUrl}get-started/users/"
            
            val response = api.getRawHtmlResponse(url)
            if (!response.isSuccessful) return@withContext emptyList()
            
            val html = response.body()?.string() ?: return@withContext emptyList()
            val document = org.jsoup.Jsoup.parse(html)
            val users = mutableListOf<SuggestedUser>()
            
            val userElements = document.select(".column.is-flex .box a.has-text-default")
            for (element in userElements) {
                var profileUrl = element.attr("href")
                if (profileUrl.isNotEmpty() && !profileUrl.startsWith("http")) {
                    val cleanSrc = profileUrl.trimStart('/')
                    profileUrl = "$baseUrl$cleanSrc"
                }

                val nameElement = element.selectFirst("span.has-text-weight-bold")
                val name = nameElement?.attr("title")?.ifEmpty { nameElement.text() } ?: ""
                
                val handleSpan = element.select("span").lastOrNull { it.text().startsWith("@") }
                val handle = handleSpan?.attr("title")?.ifEmpty { handleSpan.text() } ?: ""
                
                val avatarImg = element.select("img").firstOrNull()
                var avatarUrl = avatarImg?.attr("src") ?: ""
                if (avatarUrl.isNotEmpty() && !avatarUrl.startsWith("http")) {
                    val cleanSrc = avatarUrl.trimStart('/')
                    avatarUrl = "$baseUrl$cleanSrc"
                }
                
                users.add(SuggestedUser(name, handle, avatarUrl, profileUrl))
            }
            users
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getUnreadNotificationCount(api: BookWyrmApi, instanceUrl: String): Int = withContext(Dispatchers.IO) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val html = fetchHtmlWithRedirects(api, baseUrl, baseUrl)
            if (html.isEmpty()) return@withContext 0
            
            val document = org.jsoup.Jsoup.parse(html)
            val badge = document.select("strong[data-poll=notifications]").first()
            val text = badge?.text()?.trim() ?: ""
            if (text.isNotEmpty()) text.toIntOrNull() ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun scrapeNotifications(api: BookWyrmApi, instanceUrl: String): List<NotificationUiItem> = withContext(Dispatchers.IO) {
        val notifications = mutableListOf<NotificationUiItem>()
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val notifUrl = "${baseUrl}notifications"
            
            val html = fetchHtmlWithRedirects(api, notifUrl, baseUrl)
            if (html.isEmpty()) return@withContext emptyList()
            
            val document = org.jsoup.Jsoup.parse(html)
            val elements = document.select("div.notification:not(.live-message)")
            for (element in elements) {
                val id = element.attr("id").ifEmpty { java.util.UUID.randomUUID().toString() }
                val isUnread = element.hasClass("unread")
                
                val contentHtml = element.select(".content").firstOrNull()?.html() ?: element.text()
                
                val avatarImg = element.select("img").firstOrNull()
                var avatarUrl = avatarImg?.attr("src") ?: ""
                if (avatarUrl.isNotEmpty() && !avatarUrl.startsWith("http")) {
                    avatarUrl = "$baseUrl${avatarUrl.trimStart('/')}"
                }
                
                val actorName = element.select("strong").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
                    ?: element.select("a").firstOrNull { it.text().isNotBlank() }?.text() 
                    ?: "Unknown"
                    
                val timeElement = element.select("time").firstOrNull()
                val date = timeElement?.text() ?: ""
                
                var permalink = element.select("a.time, .status-link").firstOrNull()?.attr("href")?.let {
                    if (it.startsWith("http")) it else "$baseUrl${it.trimStart('/')}"
                }
                
                if (permalink == null) {
                    val profileHref = element.select("a[href^=/user/]").firstOrNull { it.text().isNotBlank() }?.attr("href")
                    if (profileHref != null) {
                        permalink = if (profileHref.startsWith("http")) profileHref else "$baseUrl${profileHref.trimStart('/')}"
                    }
                }
                
                val fullText = element.text().lowercase()
                val type = when {
                    fullText.contains("replied") || fullText.contains("respondió") -> NotificationType.REPLY
                    fullText.contains("mention") || fullText.contains("mencionó") -> NotificationType.MENTION
                    fullText.contains("favorite") || fullText.contains("favorito") || fullText.contains("gusta") -> NotificationType.FAVORITE
                    fullText.contains("boost") || fullText.contains("compartió") -> NotificationType.BOOST
                    fullText.contains("follow") || fullText.contains("siguió") || fullText.contains("sigue") || fullText.contains("segue") -> NotificationType.FOLLOW
                    else -> NotificationType.UNKNOWN
                }
                
                // BookWyrm sometimes splits a notification into the header ("User boosted...") and the embedded post ("<article>...").
                // If this element looks like an embedded post, merge its content into the previous notification.
                val isEmbeddedPost = element.select("article, .status").isNotEmpty() || type == NotificationType.UNKNOWN
                if (isEmbeddedPost && notifications.isNotEmpty()) {
                    val prev = notifications.removeAt(notifications.lastIndex)
                    val embeddedHtml = element.select(".content").firstOrNull()?.html() ?: element.html()
                    val mergedContent = prev.content + "<br><br>" + embeddedHtml
                    notifications.add(prev.copy(content = mergedContent))
                    continue
                }
                
                notifications.add(
                    NotificationUiItem(
                        id = id.ifEmpty { java.util.UUID.randomUUID().toString() },
                        isUnread = isUnread,
                        type = type,
                        actorName = actorName,
                        actorAvatarUrl = avatarUrl.ifEmpty { null },
                        date = date,
                        content = contentHtml,
                        permalink = permalink
                    )
                )
            }
            notifications
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Resultado de [clearNotifications]. [detail] indica el motivo del fallo (código HTTP o
     * etapa) para poder informar al usuario en lugar de fallar en silencio.
     */
    data class ClearResult(val success: Boolean, val detail: String)

    /**
     * Borra en el servidor las notificaciones ya leídas.
     *
     * @param api debe ser el cliente creado por [NetworkClient.createAuthenticatedApi]
     *   (el que mantiene el CookieJar de sesión); con otro cliente el POST recibe un 403.
     */
    suspend fun clearNotifications(api: BookWyrmApi, instanceUrl: String): ClearResult = withContext(Dispatchers.IO) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val notifUrl = "${baseUrl}notifications"
            
            // Esta descarga también marca las notificaciones como leídas: BookWyrm solo borra
            // las leídas (`notification_set.filter(read=True).delete()`) al recibir el POST.
            val html = fetchHtmlWithRedirects(api, notifUrl, baseUrl)
            if (html.isEmpty()) {
                android.util.Log.w(TAG_CLEAR, "GET $notifUrl devolvió HTML vacío")
                return@withContext ClearResult(false, "HTML")
            }

            // Se envía el valor de la cookie csrftoken, no el token enmascarado del HTML:
            // Django valida el campo del formulario contra la cookie que viaja en la petición,
            // y solo el CookieJar de [NetworkClient] refleja las rotaciones de csrftoken.
            // El token del HTML queda como respaldo. Mismo criterio que la actualización de
            // progreso (BookDetailsDialog). IMPORTANTE: el POST debe hacerlo el mismo cliente
            // cuyo jar se consulta aquí; usar el api inyectado por Hilt (sin CookieJar) da 403.
            val csrfToken = NetworkClient.currentCsrfToken()
                ?: org.jsoup.Jsoup.parse(html).select("input[name=csrfmiddlewaretoken]").attr("value")

            if (csrfToken.isEmpty()) {
                android.util.Log.w(TAG_CLEAR, "no se encontró ningún token CSRF")
                return@withContext ClearResult(false, "CSRF")
            }

            val response = api.postClearNotifications(csrfToken)
            // La vista de BookWyrm borra y termina con `redirect("/notifications")`, así que la
            // respuesta normal es un 302. El cliente de sesión no sigue redirecciones
            // (followRedirects(false)), por lo que un 3xx cuenta como éxito igual que un 2xx.
            val success = response.isSuccessful || response.code() in 300..399
            if (!success) {
                android.util.Log.w(
                    TAG_CLEAR,
                    "POST notifications → HTTP ${response.code()} " +
                        "cuerpo=${response.errorBody()?.string()?.take(200)?.replace("\n", " ")}"
                )
            }
            ClearResult(success, "HTTP ${response.code()}")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e(TAG_CLEAR, "clearNotifications falló", e)
            ClearResult(false, e.message ?: e.javaClass.simpleName)
        }
    }
}

/**
 * Función de extensión que empaqueta una petición multipart para editar el perfil (Avatar, biografía).
 */
suspend fun com.ferlagod.rocinante.data.api.BookWyrmApi.editProfile(
    name: String,
    summary: String,
    csrfToken: String
): retrofit2.Response<okhttp3.ResponseBody> {
    val getResponse = this.getProfilePreferences()
    if (!getResponse.isSuccessful) {
        return getResponse
    }

    val html = getResponse.body()?.string() ?: return getResponse
    val document = org.jsoup.Jsoup.parse(html)
    val form = document.selectFirst("form[method=post]")
        ?: return retrofit2.Response.error(400, "Form not found".toByteArray().toResponseBody(null))

    val formBuilder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
    formBuilder.addFormDataPart("csrfmiddlewaretoken", csrfToken)

    val inputs = form.select("input, textarea, select")
    for (input in inputs) {
        val fieldName = input.attr("name")
        if (fieldName.isBlank() || fieldName == "csrfmiddlewaretoken") continue

        val type = input.attr("type").lowercase()
        if (type == "file") continue

        if (fieldName == "name") {
            formBuilder.addFormDataPart(fieldName, name)
            continue
        }
        if (fieldName == "summary" || fieldName == "bio" || fieldName == "description") {
            formBuilder.addFormDataPart(fieldName, summary)
            continue
        }

        if (type == "checkbox" || type == "radio") {
            if (input.hasAttr("checked")) {
                formBuilder.addFormDataPart(fieldName, input.attr("value").ifEmpty { "on" })
            }
        } else {
            val value = if (input.tagName() == "textarea") input.text() else input.attr("value")
            formBuilder.addFormDataPart(fieldName, value)
        }
    }

    return this.submitProfilePreferences(formBuilder.build())
}
