/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.data.api

import com.ferlagod.rocinante.utils.BookWyrmUtils

import com.google.gson.annotations.SerializedName
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.Headers
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Representa el perfil público y la información básica de un actor/usuario
 * en BookWyrm, incluyendo contadores de seguidores extraídos.
 */
data class BookWyrmProfile(
    val id: String?,
    val type: String?,
    val name: String?,
    val summary: String?,
    val outbox: String?,
    val inbox: String?,
    val icon: ProfileIcon?,
    // preferredUsername es el handle local (sin @instance) según la spec ActivityPub
    val preferredUsername: String?,
    // Se cambia de Int a String para capturar las URLs federadas de las colecciones
    val followers: String?,
    val following: String?,
    // Campos locales para la interfaz gráfica (no vienen del JSON de perfil)
    var followersCountLocal: Int = 0,
    var followingCountLocal: Int = 0
)

data class ProfileIcon(
    val url: String?
)

data class ActivityPubCollection(
    @SerializedName("totalItems") val totalItems: Int?
)

data class OutboxPage(
    @SerializedName("orderedItems") val orderedItems: List<ActivityPubActivity>?
)

/**
 * Representa una actividad individual en el estándar ActivityPub (Nota, Reseña, Artículo, etc.).
 */
data class ActivityPubActivity(
    val id: String?,
    val type: String?,
    val published: String?,
    val actor: String?,
    val content: String? = null,
    val name: String? = null,
    @SerializedName("object") val rawObjectData: com.google.gson.JsonElement? = null,
    val actorAvatarUrl: String? = null
) {
    val objectData: ActivityPubObject?
        get() = if (rawObjectData != null && rawObjectData.isJsonObject) {
            com.google.gson.Gson().fromJson(rawObjectData, ActivityPubObject::class.java)
        } else null
}

data class ActivityPubObject(
    val id: String?,
    val type: String?,
    val content: String?,
    val name: String?,
    // Calificación por estrellas (Review)
    val rating: Int?,
    // URL del libro al que hace referencia la actividad (Review, Comment, Quotation)
    @SerializedName("inReplyToBook") val inReplyToBook: String? = null,
    // Adjuntos: BookWyrm puede incluir aquí la portada del libro
    val attachment: List<ActivityPubAttachment>? = null,
    // Portada directa si el objeto es un Libro (Edition, Work, etc)
    val cover: ActivityPubAttachment? = null
)

data class ActivityPubAttachment(
    val url: String? = null,
    val mediaType: String? = null,
    val name: String? = null
)

// Colección de usuarios seguidos (ActivityPub OrderedCollection)
data class FollowingPage(
    @SerializedName("orderedItems") val orderedItems: List<String>?
)

data class BookSearchResult(
    val key: String?,
    val title: String?,
    val author: String?,
    val year: Int?,
    val cover: String?
)

/**
 * Representa los detalles ampliados y estructurados de un libro
 * devueltos por la API de BookWyrm.
 */
data class BookWyrmBookDetails(
    val title: String?,
    val description: String?,
    val publishedDate: String?,
    val pages: Int?,
    // Nuevo campo para capturar la URL de la portada en la ficha
    val cover: ShelfBookCover?
)

// NUEVOS MODELOS PARA LA ESTANTERÍA
data class ShelfPage(
    @SerializedName("orderedItems") val orderedItems: List<ShelfBookItem>?
)

data class ShelfBookItem(
    val id: String?,
    val title: String?,
    val cover: ShelfBookCover?
)

data class ShelfBookCover(
    val url: String?
)

data class SuggestedUser(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val profileUrl: String
)

/**
 * Interfaz de Retrofit que define los endpoints (API REST) soportados para interactuar
 * con una instancia de BookWyrm. Maneja tanto las llamadas nativas de ActivityPub como
 * endpoints JSON específicos que expone BookWyrm.
 */
interface BookWyrmApi {
    @GET("user/{username}.json")
    suspend fun getUserProfile(
        @Path("username") username: String,
        @Query("t") cacheBuster: Long = System.currentTimeMillis()
    ): BookWyrmProfile

    @GET
    suspend fun getFullUserProfile(
        @Url fullUrl: String,
        @Query("t") cacheBuster: Long = System.currentTimeMillis()
    ): BookWyrmProfile

    @GET
    suspend fun getOutboxData(@Url fullUrl: String): OutboxPage

    @GET
    suspend fun getInboxData(@Url fullUrl: String): OutboxPage

    @GET
    suspend fun getRawJson(@Url fullUrl: String): ResponseBody

    @FormUrlEncoded
    @POST("resolve-book/")
    suspend fun resolveBook(
        @Field("remote_id") remoteId: String
    ): retrofit2.Response<ResponseBody>

    // BookWyrm expone búsqueda JSON con &format=json desde v0.6.x.
    // Instancias antiguas devolverán HTML y Gson fallará — no hay alternativa API estable.
    @GET("search")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("format") format: String = "json"
    ): List<BookSearchResult>

    @GET
    suspend fun getBookDetails(@Url fullUrl: String): BookWyrmBookDetails

    @GET
    suspend fun getShelfData(@Url fullUrl: String): ShelfPage

    // Colección de usuarios seguidos por el usuario
    @GET
    suspend fun getFollowingData(@Url fullUrl: String): FollowingPage

    // Colección de seguidores del usuario (misma estructura que following)
    @GET
    suspend fun getFollowersData(@Url fullUrl: String): FollowingPage

    // Seguir a un usuario: POST /follow/ con campo "user" = "@handle@instance"
    @FormUrlEncoded
    @POST("follow/")
    suspend fun followUser(
        @Field("user") userHandle: String
    ): retrofit2.Response<ResponseBody>

    // Dejar de seguir: POST /unfollow/ con campo "user" = "@handle@instance"
    @FormUrlEncoded
    @POST("unfollow/")
    suspend fun unfollowUser(
        @Field("user") userHandle: String
    ): retrofit2.Response<ResponseBody>

    // POST /reading-status/<status>/<book_id>/ — BookWyrm espera el estado
    // y el ID del libro en la ruta, NO como campos de formulario.
    // Statuses válidos: "want", "start", "finish", "stop"
    @FormUrlEncoded
    @POST("reading-status/{status}/{bookId}/")
    suspend fun updateReadingStatus(
        @Path("status") status: String,   // "want", "start", "finish", "stop"
        @Path("bookId") bookId: String,
        @Field("post_status") postStatus: Boolean = true,
        @Field("privacy") privacy: String = "public"
    ): retrofit2.Response<ResponseBody>

    // POST /reading-status/update/<book_id>/ — actualiza el readthrough y añade comentario.
    // Requiere el 'id' del readthrough activo (oculto en el HTML del libro).
    @FormUrlEncoded
    @POST("reading-status/update/{bookIdPath}/")
    suspend fun updateProgressDetailed(
        @Path("bookIdPath") bookIdPath: String,
        @Field("id") readthroughId: String,
        @Field("user") userId: String,
        @Field("book") book: String,
        @Field("progress") progress: String,
        @Field("progress_mode") progressMode: String = "PG",
        @Field("post-status") postStatus: String = "on",
        @Field("privacy") privacy: String = "public",
        @Field("content") content: String = "",
        @Field("content_warning") contentWarning: String = ""
    ): retrofit2.Response<ResponseBody>

    @FormUrlEncoded
    @POST("post/review/")
    suspend fun postReview(
        @Field("book") book: String,
        @Field("user") user: String,
        @Field("name") name: String,
        @Field("content") content: String,
        @Field("rating") rating: String,
        @Field("privacy") privacy: String,
        @Field("content_warning") contentWarning: String,
        @Field("sensitive") sensitive: Boolean
    ): retrofit2.Response<okhttp3.ResponseBody>


    @GET("preferences/profile/")
    suspend fun getProfilePreferences(): retrofit2.Response<okhttp3.ResponseBody>

    @POST("preferences/profile/")
    suspend fun submitProfilePreferences(
        @Body body: okhttp3.RequestBody
    ): retrofit2.Response<okhttp3.ResponseBody>


    @FormUrlEncoded
    @POST("shelve/")
    suspend fun shelveBook(
        // BookWyrm espera el ID numérico del libro en el campo "book".
        // El campo "book_id" no existe en la vista Django y se ignora.
        @Field("book") bookId: String,
        @Field("shelf") shelfType: String
    ): retrofit2.Response<ResponseBody>


    // POST /favorite/<status_id>
    @Headers("Accept: application/json")
    @POST("favorite/{statusId}/")
    suspend fun favoriteStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>

    // POST /unfavorite/<status_id>
    @Headers("Accept: application/json")
    @POST("unfavorite/{statusId}/")
    suspend fun unfavoriteStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>

    // POST /post/reply/
    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("post/reply/")
    suspend fun replyStatus(
        @Field("user") userId: String,
        @Field("content") content: String,
        @Field("reply_parent") replyParent: String,
        @Field("privacy") privacy: String = "public",
        @Field("csrfmiddlewaretoken") csrfToken: String
    ): retrofit2.Response<okhttp3.ResponseBody>

    // POST /post/status/ (for new status)
    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("post/status/")
    suspend fun createStatus(
        @Field("user") userId: String,
        @Field("content") content: String,
        @Field("privacy") privacy: String = "public",
        @Field("csrfmiddlewaretoken") csrfToken: String
    ): retrofit2.Response<okhttp3.ResponseBody>

    // NUEVO METODO PARA EXTRAER LOS CONTADORES DE LAS COLECCIONES
    @GET
    suspend fun getCollectionData(@Url fullUrl: String): ActivityPubCollection

    // NUEVO METODO PARA OBTENER HTML PURO
    @GET
    suspend fun getRawHtml(@Url fullUrl: String): retrofit2.Response<okhttp3.ResponseBody>

    // Anotación corregida: @Url puro, requiere pasar la dirección absoluta desde el UI
    @GET
    @Headers("Accept: text/html")
    suspend fun getRawHtmlResponse(@Url fullUrl: String): retrofit2.Response<ResponseBody>
}

/**
 * CookieJar mutable que:
 * 1. Inicializa las cookies a partir del string de sesión guardado en DataStore.
 * 2. Actualiza automáticamente `csrftoken` con cada respuesta Set-Cookie del servidor.
 *    Esto es imprescindible para Django, que rota el token CSRF en cada respuesta POST.
 */
class SessionCookieJar(initialCookieString: String, private val host: String) : CookieJar {

    // Mapa mutable nombre → valor; se actualiza con cada respuesta
    private val cookieMap: MutableMap<String, String> = mutableMapOf()

    init {
        // Parsear el string "name=value; name2=value2" inicial
        initialCookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    cookieMap[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
                }
            }
    }

    /** Devuelve el valor actual del csrftoken (puede haber rotado). */
    fun currentCsrfToken(): String? = cookieMap["csrftoken"]

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Solo guardar cookies si la respuesta proviene del host principal
        if (!url.host.contains(host)) return
        
        // Actualizar el mapa con los nuevos valores enviados por el servidor
        cookies.forEach { cookie ->
            cookieMap[cookie.name] = cookie.value
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Solo enviar cookies si la petición se dirige al host principal
        if (!url.host.contains(host)) return emptyList()
        
        return cookieMap.map { (name, value) ->
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(url.host)
                .build()
        }
    }
}

/**
 * Cliente centralizado para la configuración y creación de conexiones de red a BookWyrm.
 * Utiliza Retrofit y OkHttp, configurando interceptores esenciales para el manejo de sesiones 
 * y tokens CSRF requeridos por el backend de Django en BookWyrm.
 */
object NetworkClient {
    /** Último OkHttpClient creado; permite acceder al CookieJar desde la UI. */
    var lastOkHttpClient: OkHttpClient? = null
        private set

    fun createAuthenticatedApi(baseUrl: String, cookieString: String): BookWyrmApi {
        val cleanUrl = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
        val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        val host = finalUrl.toHttpUrlOrNull()?.host ?: cleanUrl

        // CookieJar con las cookies de sesión iniciales
        val cookieJar = SessionCookieJar(cookieString, host)

        val interceptor = Interceptor { chain ->
            // Leer el CSRF más reciente del jar (puede haber rotado tras un POST previo)
            val csrfToken = cookieJar.currentCsrfToken()

            val requestBuilder = chain.request().newBuilder()
                .addHeader("Referer", finalUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 Rocinante/1.0")

            val acceptHeader = chain.request().header("Accept")
            if (acceptHeader?.contains("text/html") != true) {
                requestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")
            }

            if (acceptHeader == null) {
                requestBuilder.addHeader("Accept", "application/activity+json, application/json")
            }

            if (csrfToken != null) {
                requestBuilder.addHeader("X-CSRFToken", csrfToken)
            }

            chain.proceed(requestBuilder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)   // OkHttp gestiona las cookies automáticamente
            .addInterceptor(interceptor)
            .followRedirects(false) // No seguir redirects automáticamente: capturar 302
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        lastOkHttpClient = okHttpClient

        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BookWyrmApi::class.java)
    }

    data class ProgressContext(
        val readthroughId: String,
        val userId: String,
        val localBookId: String
    )
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

            val userRegex = """name=["']user["']\s+value=["'](\d+)["']""".toRegex()
            val userMatch = userRegex.find(html)
            val userId = userMatch?.groupValues?.get(1)

            if (userId != null) {
                ReviewContext(userId, bookId)
            } else null
        } catch (e: Exception) {
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
                bookUrl
            }
        }
    }

    /**
     * Obtiene el contexto necesario para actualizar el progreso (readthrough ID y user ID).
     * Si la URL pertenece a un libro federado, primero lo resuelve a su URL local.
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
                    
                    val regex = """<form[^>]*name=["']reading-progress-[^>]*>.*?name=["']id["'][^>]*value=["'](\d+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val match = regex.find(html)
                    val readthroughId = match?.groupValues?.get(1)
                    
                    val userRegex = """name=["']user["']\s+value=["'](\d+)["']""".toRegex()
                    val userMatch = userRegex.find(html)
                    val userId = userMatch?.groupValues?.get(1)
                    
                    if (readthroughId != null && userId != null) {
                        return@withContext ProgressContext(readthroughId, userId, localBookId)
                    } else {
                        return@withContext null
                    }
                }
                
                val html = response.body()?.string() ?: return@withContext null

                // Buscar el formulario de actualización de progreso y extraer su ID oculto
                val regex = """<form[^>]*name=["']reading-progress-[^>]*>.*?name=["']id["'][^>]*value=["'](\d+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = regex.find(html)
                val readthroughId = match?.groupValues?.get(1)

                // Buscar el user ID
                val userRegex = """name=["']user["']\s+value=["'](\d+)["']""".toRegex()
                val userMatch = userRegex.find(html)
                val userId = userMatch?.groupValues?.get(1)

                if (readthroughId != null && userId != null) {
                    ProgressContext(readthroughId, userId, localBookId)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Raspa el HTML de la página del libro para obtener las reseñas de la comunidad.
     * BookWyrm no tiene un endpoint JSON para devolver las reseñas de un libro de forma paginada.
     */
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

            // Buscar todas las publicaciones y calificaciones dentro del bloque #reviews
            val statusElements = document.select("#reviews article.card, #reviews .block.is-flex .media")

            for ((index, element) in statusElements.withIndex()) {
                // Extraer el autor
                val authorElement = element.selectFirst("[itemprop=author] a") ?: element.selectFirst(".card-header-title a")
                val authorName = element.selectFirst("[itemprop=name]")?.text() ?: authorElement?.text()?.trim() ?: "Unknown"
                val actorUrl = authorElement?.attr("href") ?: ""

                // Extraer el contenido
                val contentElement = element.selectFirst("[itemprop=reviewBody]") ?: element.selectFirst("div.content")
                val contentText = contentElement?.html() ?: ""

                // Extraer el título (solo en Reviews formales)
                val titleElement = element.selectFirst("h3[itemprop=name]")
                val titleText = titleElement?.html()?.trim() ?: ""

                // Unificar título y contenido para mostrarlo en el ActivityPubActivity
                val finalContent = if (titleText.isNotEmpty()) {
                    "<strong>$titleText</strong><br><br>$contentText"
                } else {
                    contentText
                }

                // Extraer la calificación
                val ratingMeta = element.selectFirst("meta[itemprop=ratingValue]")
                val ratingValue = ratingMeta?.attr("content")?.toFloatOrNull()?.toInt()

                // Extraer la fecha
                val dateElement = element.selectFirst("time")
                var publishedDate = dateElement?.attr("datetime") ?: ""
                if (publishedDate.isEmpty()) {
                    // Try to find the date from the permalink text
                    val dateLink = element.select("a").firstOrNull {
                        it.attr("href").contains("/reviewrating/") || it.attr("href").contains("/review/") || it.attr("href").contains("/status/")
                    }
                    if (dateLink != null) {
                        publishedDate = dateLink.text()
                    }
                }

                // Extraer el avatar del autor
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

                // Construir ActivityPubActivity simulado
                val activity = ActivityPubActivity(
                    id = "$bookUrl/review/$index",
                    type = "Review",
                    actor = actorUrl,
                    name = authorName,
                    published = publishedDate,
                    rawObjectData = com.google.gson.Gson().toJsonTree(com.ferlagod.rocinante.data.api.ActivityPubObject(
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
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Raspa la página "get-started/users/" para extraer los usuarios sugeridos a seguir.
     */
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
            e.printStackTrace()
            emptyList()
        }
    }
}

suspend fun com.ferlagod.rocinante.data.api.BookWyrmApi.editProfile(
    name: String,
    summary: String,
    csrfToken: String
): retrofit2.Response<okhttp3.ResponseBody> {
    // 1. Get the current profile preferences page
    val getResponse = this.getProfilePreferences()
    if (!getResponse.isSuccessful) {
        return getResponse
    }

    val html = getResponse.body()?.string() ?: return getResponse
    val document = org.jsoup.Jsoup.parse(html)
    val form = document.selectFirst("form[method=post]")
        ?: return retrofit2.Response.error(400, "Form not found".toByteArray().toResponseBody(null))

    // 2. Extract all existing form fields
    val formBuilder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)

    // Make sure we include the CSRF token
    formBuilder.addFormDataPart("csrfmiddlewaretoken", csrfToken)

    val inputs = form.select("input, textarea, select")
    for (input in inputs) {
        val fieldName = input.attr("name")
        if (fieldName.isBlank() || fieldName == "csrfmiddlewaretoken") continue

        val type = input.attr("type").lowercase()
        if (type == "file") continue

        // Override name and summary
        if (fieldName == "name") {
            formBuilder.addFormDataPart(fieldName, name)
            continue
        }
        if (fieldName == "summary" || fieldName == "bio" || fieldName == "description") {
            formBuilder.addFormDataPart(fieldName, summary)
            continue
        }

        // Only include checkboxes if they are checked
        if (type == "checkbox" || type == "radio") {
            if (input.hasAttr("checked")) {
                formBuilder.addFormDataPart(fieldName, input.attr("value").ifEmpty { "on" })
            }
        } else {
            // For other inputs (text, email, hidden, select, textarea)
            val value = if (input.tagName() == "textarea") input.text() else input.attr("value")
            formBuilder.addFormDataPart(fieldName, value)
        }
    }

    // 3. Post the reconstructed form
    return this.submitProfilePreferences(formBuilder.build())
}