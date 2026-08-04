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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * 3 → añade la serie del libro: nombre, enlace y número dentro de ella.
     * 4 → añade la estantería en la que está, el aviso de otra edición ya guardada y las
     *     lecturas una a una.
     * 5 → añade el identificador (sin traducir) de la estantería donde está la otra edición.
     */
    const val ENRICHMENT_SCHEMA_VERSION = 5

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
            val localUrl = resolveLocalBookUrl(api, bookUrl) ?: bookUrl
            val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
            
            val html = fetchBookPage(api, localUrl, baseUrl)
            if (html.isEmpty()) return@withContext null

            val userId = extractHiddenFieldValue(html, "user")
            val formBookId = extractEditionId(html) ?: BookWyrmUtils.extractBookId(localUrl)

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
     * URL limpia y completa de un libro, sea cual sea lo que traiga cada pantalla: la
     * estantería da la URL entera, la búsqueda solo el número, y cualquiera de las dos puede
     * venir con el sufijo del título («/s/…») o con «.json».
     *
     * Es además la clave con la que se guarda el enriquecimiento, así que lo que se lee
     * abriendo un libro desde la búsqueda lo encuentra después la estantería.
     *
     * @return La URL normalizada; lo recibido tal cual si aún no se sabe de qué instancia se
     *   trata (sin sesión no hay forma de completar un identificador suelto).
     */
    fun canonicalBookUrl(bookUrl: String): String {
        val full = if (bookUrl.startsWith("http")) {
            bookUrl
        } else {
            val host = NetworkClient.lastInstanceHost?.takeIf { it.isNotBlank() } ?: return bookUrl
            "https://$host/book/${com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(bookUrl)}"
        }
        return full.substringBefore("/s/").removeSuffix(".json").trimEnd('/')
    }

    /**
     * Resuelve un libro federado a su URL local siguiendo la redirección de resolve-book.
     *
     * Un libro que ya es de la instancia se devuelve tal cual, sin preguntar: resolve-book no
     * es una consulta sino un POST que *importa* el libro remoto a la base de datos local, así
     * que llamarlo para algo que ya está en casa es una petición de más por libro. Y son
     * muchas: los libros de las propias estanterías son todos locales.
     */
    suspend fun resolveLocalBookUrl(api: BookWyrmApi, bookUrl: String): String? {
        // Se normaliza a la entrada: la búsqueda llega con el identificador suelto («2350350»),
        // que sin completar no es una URL y hace fallar todo lo que viene detrás.
        @Suppress("NAME_SHADOWING") val bookUrl = canonicalBookUrl(bookUrl)
        if (isLocalBookUrl(bookUrl)) return bookUrl
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

    // ── La página del libro, pedida una sola vez ──
    // Al abrir una ficha, las reseñas y el enriquecimiento quieren la misma página en el mismo
    // instante, y cada uno la pedía por su cuenta. Se guarda la última leída unos segundos; el
    // candado hace además que quien llegue segundo espere al primero en vez de pedirla otra vez.
    private val bookPageLock = Mutex()
    private var bookPageKey: String? = null
    private var bookPageHtml: String = ""
    private var bookPageAt: Long = 0L
    private const val BOOK_PAGE_REUSE_MS = 15_000L

    /**
     * Clave con la que se reconoce que dos peticiones van a la misma página. Cada lado llega
     * con la URL que tiene a mano —una con el sufijo del título, otra sin él, otra con .json—
     * y todas son el mismo libro.
     */
    private fun bookPageKeyOf(url: String): String =
        url.substringBefore("/s/").removeSuffix(".json").trimEnd('/').lowercase()

    /**
     * Descarga la página HTML de un libro, reutilizando la última si es la misma y acaba de
     * leerse.
     *
     * @return El HTML, o "" si no se pudo leer (en cuyo caso no se guarda nada).
     */
    suspend fun fetchBookPage(api: BookWyrmApi, url: String, baseUrl: String): String =
        bookPageLock.withLock {
            val key = bookPageKeyOf(url)
            val now = System.currentTimeMillis()
            if (bookPageKey == key && bookPageHtml.isNotEmpty() && now - bookPageAt < BOOK_PAGE_REUSE_MS) {
                return@withLock bookPageHtml
            }
            val html = fetchHtmlWithRedirects(api, url, baseUrl)
            if (html.isNotEmpty()) {
                bookPageKey = key
                bookPageHtml = html
                bookPageAt = now
            }
            html
        }

    /**
     * ¿Es este libro de la instancia con la que se ha iniciado sesión?
     *
     * @return false si no se sabe (aún no hay sesión) o la URL no se puede leer, de modo que
     *   ante la duda se resuelve como hasta ahora.
     */
    private fun isLocalBookUrl(bookUrl: String): Boolean {
        val instanceHost = NetworkClient.lastInstanceHost?.takeIf { it.isNotBlank() } ?: return false
        val host = runCatching { java.net.URL(bookUrl).host }.getOrNull()?.lowercase()
        return host != null && host == instanceHost
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
     * Extrae el ID de la edición del libro del HTML de la página.
     * En BookWyrm >= 0.8, las URLs a menudo son de "obras" (works) pero los formularios
     * exigen IDs de edición. BookWyrm resuelve la obra a la edición predeterminada al renderizar la página.
     */
    fun extractEditionId(html: String): String? {
        return extractHiddenFieldValue(html, "book") ?: extractHiddenFieldValue(html, "mention_books")
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
                val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
                val html = fetchBookPage(api, localUrl, baseUrl)
                if (html.isEmpty()) return@withContext null

                val localBookId = BookWyrmUtils.extractBookId(localUrl)
                val readthroughId = extractReadthroughId(html)
                val userId = extractHiddenFieldValue(html, "user")
                val startDate = extractHiddenFieldValue(html, "start_date")?.takeIf { it.isNotBlank() }
                val formBookId = extractEditionId(html) ?: localBookId

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
            val html = fetchBookPage(api, localUrl, baseUrl)
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

            // Cada lectura por separado, desde los modales edit_readthrough_<id>: son los únicos
            // sitios de la página con las fechas en ISO. Lo que se ve escrito en la lista está
            // traducido y redondeado («ayer», «14 de julio»), así que no sirve para leerlo.
            val readthroughs = doc.select("[id^=edit_readthrough_]").mapNotNull { modal ->
                val id = modal.id().removePrefix("edit_readthrough_").trim().ifEmpty { null }
                    ?: return@mapNotNull null
                val start = modal.selectFirst("input[name=start_date]")
                    ?.attr("value")?.trim()?.ifEmpty { null }
                val finish = modal.selectFirst("input[name=finish_date]")
                    ?.attr("value")?.trim()?.ifEmpty { null }
                if (start == null && finish == null) null
                else com.ferlagod.rocinante.data.model.ReadthroughDates(id, start, finish)
            }.sortedWith(compareBy(nullsLast()) { it.started ?: it.finished })

            // Resumen de todas las lecturas: el inicio más antiguo y el fin más reciente. Es lo
            // que enseñan las listas, y lo que ya había antes de guardarlas una a una.
            val finished = readthroughs.mapNotNull { it.finished }.maxOrNull()
            val started = readthroughs.mapNotNull { it.started }.minOrNull()

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

            // En qué estantería está. El botón grande de la página no dice dónde está el libro
            // sino adónde va el siguiente paso («Empezar a leer», «Terminar»...): de todas las
            // opciones deja visible una sola, la del paso siguiente, y esconde el resto. Se lee
            // esa y se deshace la cuenta. Es lo único de la página que lo dice sin depender del
            // idioma, y sin ello un libro abierto desde la búsqueda o la actividad no sabe que
            // ya se está leyendo.
            val nextStep = doc.selectFirst("[data-shelve-button-book]")
                ?.select("[data-shelf-identifier]")
                ?.firstOrNull { !it.hasClass("is-hidden") }
                ?.attr("data-shelf-identifier")?.trim()
            val shelfSlug = when (nextStep) {
                "reading" -> "to-read"
                "read" -> "reading"
                "complete" -> "read"
                "stopped-reading-complete" -> "stopped-reading"
                // "to-read" (o nada) es el paso siguiente tanto del libro que no está en ninguna
                // estantería como del que está en una propia del usuario; los distingue [shelfId].
                else -> null
            }

            // Otra edición del mismo libro ya en una estantería. El aviso está traducido, pero
            // sus dos enlaces no: uno lleva a la edición que el usuario tiene y el otro a la
            // estantería en la que está. Se localiza por el formulario de cambiar de edición,
            // que la instancia mete en el mismo párrafo.
            val otherEditionBlock = doc.selectFirst("form[name=switch-edition]")?.parent()
            val otherEditionUrl = otherEditionBlock?.select("a[href*=/book/]")
                ?.firstOrNull()?.attr("href")?.trim()?.ifEmpty { null }
                ?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + it }
            val otherEditionShelfLink = otherEditionBlock?.select("a[href*=/books/]")?.firstOrNull()
            val otherEditionShelfName = otherEditionShelfLink?.text()?.trim()?.ifEmpty { null }
            // El nombre del aviso lo escribe la instancia en su idioma («Read»), así que se
            // guarda además el identificador de la estantería, que sale del enlace y no está
            // traducido: con él la app puede decirlo con sus propias palabras.
            val otherEditionShelfSlug = otherEditionShelfLink?.attr("href")?.trim()
                ?.trimEnd('/')?.substringAfterLast('/')?.ifEmpty { null }

            // Serie del libro. Viene marcada con microdatos schema.org, así que el nombre, el
            // número y el enlace son propiedades y no hay que leerlos del texto, que está
            // traducido y cambia de idioma en idioma:
            //   <span itemprop="isPartOf" itemtype=".../BookSeries">
            //     Book <span itemprop="position">5</span> in <em><a href="/series/…">Nombre</a></em>
            //   </span>
            // Se exige que el bloque lleve el enlace a la serie: las plantillas antiguas también
            // marcaban isPartOf en un <meta> suelto, que no lleva ninguno de los tres datos.
            val seriesBlock = doc.select("[itemprop=isPartOf]")
                .firstOrNull { it.selectFirst("a[href*=/series/]") != null }
            val seriesLink = seriesBlock?.selectFirst("a[href*=/series/]")
            val seriesName = seriesLink?.text()?.trim()?.ifEmpty { null }
            // La instancia da el enlace relativo; se guarda absoluto porque es la clave con la
            // que se agrupan los libros de una misma serie.
            val seriesUrl = seriesLink?.attr("href")?.trim()?.ifEmpty { null }
                ?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + it }
            val seriesPosition = seriesBlock?.selectFirst("[itemprop=position]")
                ?.text()?.trim()?.toIntOrNull()

            com.ferlagod.rocinante.data.model.BookEnrichment(
                // Clave normalizada, no lo que trajera quien llamó: así lo que se lee abriendo
                // un libro desde la búsqueda lo encuentra luego la estantería, que lo tiene
                // apuntado con su URL completa.
                bookId = canonicalBookUrl(localUrl),
                authorName = authorName,
                rating = rating,
                finished = finished,
                started = started,
                language = language,
                shelfBookId = shelfBookId,
                shelfId = shelfId,
                seriesName = seriesName,
                seriesUrl = seriesUrl,
                seriesPosition = seriesPosition,
                shelfSlug = shelfSlug,
                otherEditionUrl = otherEditionUrl,
                otherEditionShelfName = otherEditionShelfName,
                otherEditionShelfSlug = otherEditionShelfSlug,
                readthroughs = readthroughs.ifEmpty { null },
                schemaVersion = ENRICHMENT_SCHEMA_VERSION,
                fetchedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    // Tope de páginas de ediciones que se recorren. Con las 15 por página de BookWyrm da para
    // 150 ediciones del mismo libro, muy por encima de lo que tiene ninguno.
    private const val MAX_EDITION_PAGES = 10

    /**
     * Una de las ediciones del mismo libro: la danesa, la inglesa, el audiolibro...
     *
     * @property id ID numérico, que es lo que espera `switch-edition`.
     * @property isCurrent Si es la edición desde la que se ha abierto la lista.
     */
    data class EditionOption(
        val id: String,
        val url: String,
        val title: String,
        val coverUrl: String? = null,
        val language: String? = null,
        val format: String? = null,
        val pages: Int? = null,
        val published: String? = null,
        val isCurrent: Boolean = false
    )

    /**
     * Las ediciones de un libro y los idiomas en los que existe.
     *
     * @property languages Todos los idiomas de la obra, los tenga la lista o no: la instancia
     *   los saca de todas las ediciones a la vez, así que valen para filtrar aunque el
     *   recuento por páginas se haya dejado alguna por el camino.
     */
    data class Editions(
        val editions: List<EditionOption> = emptyList(),
        val languages: List<String> = emptyList()
    )

    /**
     * Las ediciones del libro, leídas de la página `/book/<obra>/editions`.
     *
     * Cuesta una petición aparte, así que no va con el enriquecimiento: se pide solo cuando
     * alguien abre la lista. Un libro con una sola edición devuelve esa sola, y quien llame
     * decide si vale la pena enseñar la lista.
     *
     * Ojo con las ediciones de más: la instancia las ordena por un ranking con muchos empates
     * y las sirve de quince en quince, y en dos peticiones seguidas los empates no salen en el
     * mismo orden, así que al pasar de página se pierde alguna. Por eso está [language]: un
     * filtro deja el resultado en una sola página y ahí no hay nada que perder.
     *
     * @param language Idioma por el que filtrar (tal y como lo escribe la instancia, p. ej.
     *   "Danish"), o null para pedirlas todas.
     * @return Las ediciones en el orden en que las da la instancia; vacío si la página no se
     *   pudo leer o el libro no tiene enlace a sus ediciones.
     */
    suspend fun getEditions(
        api: BookWyrmApi,
        bookUrl: String,
        language: String? = null
    ): Editions = withContext(Dispatchers.IO) {
        try {
            val current = canonicalBookUrl(bookUrl)
            val localUrl = resolveLocalBookUrl(api, current) ?: current
            val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
            fun absolute(href: String): String =
                if (href.startsWith("http")) href else baseUrl.trimEnd('/') + href

            // El enlace a las ediciones está en la propia página del libro y lleva el id de la
            // obra, que no es el del ejemplar: se saca de ahí en vez de componerlo a mano.
            val bookHtml = fetchBookPage(api, localUrl, baseUrl)
            if (bookHtml.isEmpty()) return@withContext Editions()
            val editionsHref = org.jsoup.Jsoup.parse(bookHtml)
                .selectFirst("a[href~=/editions/?$]")
                ?.attr("href")?.trim()?.ifEmpty { null }
                ?: return@withContext Editions()
            val firstHref = if (language.isNullOrBlank()) editionsHref else {
                editionsHref + "?language=" + java.net.URLEncoder.encode(language, "UTF-8")
            }

            // La instancia pagina las ediciones, y de un libro muy publicado hay varias páginas:
            // quedarse con la primera escondería justo la edición que se busca. Se siguen los
            // «siguiente» hasta que se acaben, con un tope por si alguna vez se enredan.
            val pages = mutableListOf<org.jsoup.nodes.Document>()
            var nextHref: String? = firstHref
            while (nextHref != null && pages.size < MAX_EDITION_PAGES) {
                val html = fetchHtmlWithRedirects(api, absolute(nextHref), baseUrl)
                if (html.isEmpty()) break
                val page = org.jsoup.Jsoup.parse(html)
                pages += page
                nextHref = page.selectFirst("a.pagination-next:not(.is-disabled)[href]")
                    ?.attr("href")?.trim()?.ifEmpty { null }
            }
            if (pages.isEmpty()) return@withContext Editions()

            // Los idiomas del desplegable de filtros: la instancia los saca de todas las
            // ediciones de la obra a la vez, así que están todos aunque la lista no lo esté.
            val languages = pages.first().select("select#id_language option[value]")
                .mapNotNull { it.attr("value").trim().ifEmpty { null } }
                .distinct()

            // Cada edición es una fila con su enlace, su portada y los mismos microdatos que
            // la ficha (formato, páginas, idioma, fecha), que son los que la distinguen.
            val editions = pages.flatMap { it.select("div.columns.is-gapless") }.mapNotNull { row ->
                val link = row.selectFirst("h2 a[href*=/book/]")
                    ?: row.selectFirst("a[href*=/book/]")
                    ?: return@mapNotNull null
                val url = canonicalBookUrl(absolute(link.attr("href").trim()))
                val id = com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(url)
                if (id.isBlank()) return@mapNotNull null
                val title = link.text().trim().ifEmpty {
                    row.selectFirst("h2")?.text()?.trim().orEmpty()
                }
                if (title.isEmpty()) return@mapNotNull null
                EditionOption(
                    id = id,
                    url = url,
                    title = title,
                    coverUrl = row.selectFirst("img.book-cover")?.attr("src")
                        ?.trim()?.ifEmpty { null }?.let { absolute(it) },
                    language = row.selectFirst("meta[itemprop=inLanguage]")
                        ?.attr("content")?.trim()?.ifEmpty { null },
                    format = row.selectFirst("meta[itemprop=bookFormat]")
                        ?.attr("content")?.trim()?.ifEmpty { null },
                    pages = row.selectFirst("meta[itemprop=numberOfPages]")
                        ?.attr("content")?.trim()?.toIntOrNull(),
                    published = row.selectFirst("meta[itemprop=datePublished]")
                        ?.attr("content")?.trim()?.ifEmpty { null },
                    isCurrent = url == canonicalBookUrl(localUrl)
                )
            }.distinctBy { it.id }
            Editions(editions = editions, languages = languages)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Editions()
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
            ?: extractEditionId(html)
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
            val html = fetchBookPage(api, localUrl, baseUrl)
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
            val baseUrl = runCatching {
                java.net.URL(bookUrl).let { "${it.protocol}://${it.host}/" }
            }.getOrNull() ?: return@withContext emptyList()
            val html = fetchBookPage(api, bookUrl, baseUrl)
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
