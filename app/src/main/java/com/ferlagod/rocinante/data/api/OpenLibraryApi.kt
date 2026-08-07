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

import com.ferlagod.rocinante.data.model.OpenLibraryEditionsResponse
import com.ferlagod.rocinante.data.model.OpenLibrarySearchResponse
import com.ferlagod.rocinante.data.model.OpenLibrarySubject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Open Library, que es de donde salen los libros de «Explorar».
 *
 * Hace falta porque BookWyrm no sabe buscar por materia: su índice de búsqueda solo cubre
 * título, subtítulo, autor y serie, y las materias ni siquiera son enlaces en su propia
 * página del libro. Lo que se encuentre aquí entra en la instancia por `resolve-book`, que
 * sí tiene conector para openlibrary.org.
 */
interface OpenLibraryApi {

    /**
     * Busca obras con la consulta que arma [com.ferlagod.rocinante.utils.ExploreQuery].
     *
     * Va todo por `q` y no por los parámetros sueltos —`subject=`, `language=`— porque esos solo
     * admiten un filtro de cada clase y porque el de materia es impreciso: `subject=historical
     * fiction` es un O entre las palabras y devuelve 116.933 obras, mientras que la misma materia
     * entre comillas dentro de `q` devuelve 11.896. El parámetro suelto ignora las comillas.
     *
     * El filtro de idioma, esté donde esté, se queda con las obras que tengan *alguna* edición en
     * ese idioma, no con las escritas en él. Buscando en danés salen best sellers internacionales
     * con una edición danesa entre otras noventa, que es justo lo que se quiere para leer algo en
     * danés, pero obliga a filtrar también la lista de ediciones.
     *
     * @param sort Null es por relevancia. Ojo con `rating`: sin exigir un mínimo de votos ordena
     *   por una media que cualquier libro con cuatro votos generosos lidera, y por eso
     *   [com.ferlagod.rocinante.utils.ExploreQuery] le añade el suelo a la consulta.
     * @param fields Qué campos devolver. Sin esto la respuesta trae listas enormes de
     *   identificadores de Internet Archive por cada libro.
     */
    @GET("search.json")
    suspend fun search(
        @Query("q") q: String,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 24,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String =
            "key,title,author_name,first_publish_year,edition_count,cover_i"
    ): OpenLibrarySearchResponse

    /**
     * Las materias que Open Library conoce y que empiezan por lo que se va escribiendo.
     *
     * Se preguntan a ellos en vez de sacarlas de los libros propios porque una materia que ellos
     * no tengan no devuelve libros, devuelve una pantalla vacía: los catálogos de donde vienen
     * las fichas de la instancia no son el suyo. Además contesta en el idioma en que se escribe
     * —«kriminal» trae Kriminalroman, Kriminalität, Kriminalfall— y trae la clave exacta, que no
     * se puede deducir del nombre.
     *
     * Cuidado al enseñarlas: la misma clave vuelve con grafías distintas —`historical_fiction`
     * llega como «Historical fiction» con 4.655 obras y como «Historical Fiction» con 2.582—, así
     * que hay que juntarlas por clave o la lista enseña la misma materia dos veces.
     */
    @GET("subjects_autocomplete.json")
    suspend fun subjectsAutocomplete(
        @Query("q") q: String,
        @Query("limit") limit: Int = 20
    ): List<OpenLibrarySubject>

    /**
     * Las ediciones de una obra, de las que el usuario elige la suya.
     *
     * @param workId Identificador a secas, «OL17797130W».
     */
    @GET("works/{workId}/editions.json")
    suspend fun editionsOfWork(
        @Path("workId") workId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): OpenLibraryEditionsResponse
}

/**
 * Cliente de Open Library, aparte a propósito del de la instancia.
 *
 * Son dos clientes distintos por una razón que no es de estilo: el de BookWyrm lleva un tarro
 * de cookies con la sesión, la clearance de Anubis y el token CSRF, más una cabecera Referer
 * de la instancia. Reutilizarlo aquí mandaría todo eso a un tercero. Este no guarda cookies
 * de ninguna clase.
 */
object OpenLibraryClient {

    private const val BASE_URL = "https://openlibrary.org/"

    /**
     * Open Library pide que las peticiones se identifiquen y den un modo de contacto; quien no
     * lo hace se lleva un 503 antes o después. No se usa el User-Agent del WebView porque ese
     * existe para que la instancia reconozca la cookie de Anubis, y aquí no pinta nada.
     */
    private const val USER_AGENT =
        "Rocinante/1.2.0 (Android BookWyrm client; +https://github.com/ferlagod/Rocinante)"

    /**
     * Cuántas veces se reintenta antes de rendirse, y cuánto se espera entre intentos.
     *
     * Open Library falla a rachas: midiendo desde dos redes distintas hubo tandas enteras de
     * 503 y de peticiones que no contestaban nada, mientras el resto de internet respondía. Pero
     * los huecos existen —un sondeo con noventa segundos entre intentos entró al tercero—, así
     * que un solo fallo no puede ser lo que decida lo que ve el usuario. Tres intentos rápidos
     * cuestan poco y salvan la intermitencia normal; una caída de verdad no la salva nadie, y
     * para eso está el aviso que dice de quién es el fallo.
     */
    private const val MAX_ATTEMPTS = 3
    private val BACKOFF_MS = longArrayOf(700, 1800)

    val api: OpenLibraryApi by lazy {
        val client = OkHttpClient.Builder()
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                var response: okhttp3.Response? = null
                var failure: java.io.IOException? = null
                for (attempt in 0 until MAX_ATTEMPTS) {
                    if (attempt > 0) {
                        try {
                            Thread.sleep(BACKOFF_MS[(attempt - 1).coerceAtMost(BACKOFF_MS.size - 1)])
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                    response?.close()
                    response = null
                    try {
                        val candidate = chain.proceed(request)
                        // 5xx es «vuelve luego» y merece otro intento; un 404 o un 400 no va a
                        // cambiar por insistir.
                        if (candidate.code < 500) return@addInterceptor candidate
                        response = candidate
                        failure = null
                    } catch (e: java.io.IOException) {
                        failure = e
                    }
                }
                response ?: throw (failure ?: java.io.IOException("Open Library no responde"))
            }
            // Lenta cuando va bien, muda cuando va mal. Los tiempos dan margen a los reintentos
            // sin dejar la pantalla colgada para siempre.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        val gson = com.google.gson.GsonBuilder().setLenient().create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenLibraryApi::class.java)
    }

    /**
     * El idioma de la aplicación traducido al código que espera Open Library.
     *
     * Son los códigos bibliográficos de MARC21, que en seis de los diecisiete idiomas de la
     * aplicación no coinciden con los ISO 639-2/T que uno escribiría de memoria: alemán es
     * «ger» y no «deu», francés «fre» y no «fra», neerlandés «dut», rumano «rum», checo «cze»
     * y griego «gre». Equivocarse no da error, da cero resultados, así que el fallo solo lo
     * vería quien hable el idioma que no probamos.
     */
    fun marcCode(languageTag: String): String? = when (languageTag.lowercase().take(2)) {
        "da" -> "dan"
        "en" -> "eng"
        "es" -> "spa"
        "de" -> "ger"
        "fr" -> "fre"
        "nl" -> "dut"
        "ro" -> "rum"
        "cs" -> "cze"
        "el" -> "gre"
        "it" -> "ita"
        "sv" -> "swe"
        "pt" -> "por"
        "pl" -> "pol"
        "uk" -> "ukr"
        "fi" -> "fin"
        "ca" -> "cat"
        "gl" -> "glg"
        else -> null
    }

    /**
     * El camino de vuelta: del código de Open Library al de la aplicación, para poder enseñar
     * «Tysk» y no «ger» en la lista de ediciones. Null si es un idioma que no traducimos, y
     * entonces se enseña el código tal cual, que es mejor que nada.
     */
    fun localeTagForMarc(marc: String): String? = when (marc.lowercase()) {
        "dan" -> "da"
        "eng" -> "en"
        "spa" -> "es"
        "ger", "deu" -> "de"
        "fre", "fra" -> "fr"
        "dut", "nld" -> "nl"
        "rum", "ron" -> "ro"
        "cze", "ces" -> "cs"
        "gre", "ell" -> "el"
        "ita" -> "it"
        "swe" -> "sv"
        "por" -> "pt"
        "pol" -> "pl"
        "ukr" -> "uk"
        "fin" -> "fi"
        "cat" -> "ca"
        "glg" -> "gl"
        "nor" -> "no"
        "isl" -> "is"
        else -> null
    }
}
