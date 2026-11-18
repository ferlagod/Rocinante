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

import com.ferlagod.rocinante.data.model.BookSearchResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Búsqueda directa en Inventaire, además de la que haga la instancia.
 *
 * Hace falta porque la instancia no siempre llega: hay administradores que no tienen activo su
 * conector, la consulta a los catálogos se corta a los pocos segundos (SEARCH_TIMEOUT) y, sobre
 * todo, BookWyrm busca en Inventaire sin decir el idioma, así que compara con las etiquetas en
 * inglés. Un libro italiano dado de alta en Inventaire con su título en italiano no salía.
 *
 * Lo que se encuentra aquí entra en la instancia igual que lo que trae ella: por `resolve-book`
 * con la dirección que usa su conector (`https://inventaire.io/api/entities?action=by-uris&uris=…`),
 * que viene creado de serie en toda instancia de BookWyrm. Por eso la clave de cada resultado es
 * exactamente esa: así los repetidos con los de la instancia se juntan solos.
 *
 * Igual que [OpenLibraryClient], es un cliente aparte sin cookies: el de la instancia lleva la
 * sesión, y no se le manda a un tercero.
 */
object InventaireClient {

    private const val BASE_URL = "https://inventaire.io"

    /** El nombre con el que la instancia titula sus resultados, para que caigan en el mismo grupo. */
    const val SOURCE_NAME = "Inventaire"

    private const val USER_AGENT =
        "Rocinante (Android BookWyrm client; +https://github.com/ferlagod/Rocinante)"

    /** Cuántos resultados se piden. La instancia, por su parte, se queda con diez. */
    private const val LIMIT = 10

    /**
     * Lo mismo que hace el conector de BookWyrm: la puntuación de Inventaire, relativa a la del
     * primero. Lo que queda por debajo de una décima parte es ruido —buscando «una mente in
     * frammenti» el libro saca 37.000 y el siguiente, otro que solo comparte «frammenti», 500—.
     */
    private const val MIN_CONFIDENCE = 0.1

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** La dirección de una entidad («inv:…», «wd:Q…», «isbn:…») tal como la guarda el conector. */
    fun remoteId(uri: String): String = "$BASE_URL/api/entities?action=by-uris&uris=$uri"

    /**
     * Busca obras por texto. Inventaire solo indexa obras para esto: las ediciones se encuentran
     * por su ISBN ([searchByIsbn]).
     *
     * @param lang Idioma de la aplicación (código de dos letras), con el que Inventaire escoge
     *   qué etiqueta comparar y enseñar.
     */
    suspend fun searchWorks(query: String, lang: String?): List<BookSearchResult> {
        val url = "$BASE_URL/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("types", "works")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("limit", LIMIT.toString())
            .apply { lang?.takeIf { it.isNotBlank() }?.let { addQueryParameter("lang", it.take(2).lowercase()) } }
            .build()
        return getJson(url.toString())?.let { parseSearch(it) }.orEmpty()
    }

    /** La edición con ese ISBN, si Inventaire la tiene. */
    suspend fun searchByIsbn(isbn: String): List<BookSearchResult> {
        val clean = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        if (clean.isEmpty()) return emptyList()
        return getJson(remoteId("isbn:$clean"))?.let { parseEntities(it) }.orEmpty()
    }

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Inventaire es un extra: si no contesta, se queda lo que haya dado la instancia.
            null
        }
    }

    /** Lee la respuesta de /api/search. */
    internal fun parseSearch(json: JsonObject): List<BookSearchResult> {
        val results = json.getAsJsonArray("results")?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            ?: return emptyList()
        val best = results.firstNotNullOfOrNull { it.double("_score") }?.takeIf { it > 0 }
        return results.mapNotNull { result ->
            val uri = result.string("uri") ?: return@mapNotNull null
            val score = result.double("_score")
            if (best != null && score != null && score / best < MIN_CONFIDENCE) return@mapNotNull null
            val title = result.string("label") ?: return@mapNotNull null
            BookSearchResult(
                key = remoteId(uri),
                title = title,
                // Como en la web de BookWyrm: la descripción hace de autor («libro di David Graeber»).
                author = result.string("description"),
                year = null,
                cover = coverUrl(result.string("image")),
                isRemote = true,
                remoteId = remoteId(uri),
                source = SOURCE_NAME
            )
        }
    }

    /** Lee la respuesta de /api/entities?action=by-uris (la búsqueda por ISBN). */
    internal fun parseEntities(json: JsonObject): List<BookSearchResult> {
        val entities = json.getAsJsonObject("entities") ?: return emptyList()
        return entities.entrySet().mapNotNull { (key, value) ->
            val entity = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val uri = entity.string("uri") ?: key
            val claims = entity.getAsJsonObject("claims")
            val title = claims?.getAsJsonArray("wdt:P1476")?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                ?: entity.getAsJsonObject("labels")?.entrySet()?.firstOrNull()?.value?.takeIf { it.isJsonPrimitive }?.asString
                ?: return@mapNotNull null
            val year = claims?.getAsJsonArray("wdt:P577")?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                ?.take(4)?.toIntOrNull()
            val image = entity.get("image")?.let { img ->
                when {
                    img.isJsonPrimitive -> img.asString
                    img.isJsonObject -> img.asJsonObject.string("url")
                    else -> null
                }
            }
            BookSearchResult(
                key = remoteId(uri),
                title = title,
                author = null,
                year = year,
                cover = coverUrl(image),
                isRemote = true,
                remoteId = remoteId(uri),
                source = SOURCE_NAME
            )
        }
    }

    private fun coverUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> BASE_URL + "/" + path.trimStart('/')
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
