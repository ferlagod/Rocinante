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

import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.ferlagod.rocinante.data.model.NotificationUiItem
import com.ferlagod.rocinante.data.model.NotificationType

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
    var followersCountLocal: Int? = null,
    var followingCountLocal: Int? = null
    , var readingGoal: ReadingGoal? = null

)

/**
 * Modelo para representar el icono de perfil de un usuario.
 */
data class ProfileIcon(
    val url: String?
)

/**
 * Representa el progreso del reto de lectura anual extraído del HTML de BookWyrm.
 */
data class ReadingGoal(
    val max: Int,
    val value: Int
)

data class ActivityPubCollection(
    @SerializedName("totalItems") val totalItems: Int?
)

/**
 * Representa una página del 'Outbox' (Bandeja de salida) de ActivityPub, contiene actividades.
 */
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

/**
 * Objeto genérico de ActivityPub (puede ser una nota, reseña, etc.).
 */
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

/**
 * Adjunto multimedia dentro de una actividad de ActivityPub.
 */
data class ActivityPubAttachment(
    val url: String? = null,
    val mediaType: String? = null,
    val name: String? = null
)

// Colección de usuarios seguidos (ActivityPub OrderedCollection)
/**
 * Representa una página de usuarios seguidos o seguidores.
 */
data class FollowingPage(
    @SerializedName("orderedItems") val orderedItems: List<String>?
)

data class BookSearchResult(
    val key: String?,
    val title: String?,
    val author: String?,
    val year: Int?,
    val cover: String?,
    val isRemote: Boolean = false,
    val remoteId: String? = null
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
/**
 * Representa una estantería (colección de libros) de un usuario.
 */
data class ShelfPage(
    @SerializedName("orderedItems") val orderedItems: List<ShelfBookItem>?
)

data class ShelfBookItem(
    val id: String?,
    val title: String?,
    val cover: ShelfBookCover?
)

/**
 * Representación de la portada de un libro.
 */
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
    /**
     * Obtiene el perfil básico de un usuario remoto o local.
     */
    @GET("user/{username}.json")
    suspend fun getUserProfile(
        @Path("username") username: String,
        @Query("t") cacheBuster: Long = System.currentTimeMillis()
    ): BookWyrmProfile

    /**
     * Obtiene el perfil extendido (Outbox, colecciones) de un usuario.
     */
    @GET
    suspend fun getFullUserProfile(
        @Url fullUrl: String,
        @Query("t") cacheBuster: Long = System.currentTimeMillis()
    ): BookWyrmProfile

    /**
     * Descarga la página del Outbox especificada.
     */
    @GET
    suspend fun getOutboxData(@Url fullUrl: String): OutboxPage

    @GET
    suspend fun getInboxData(@Url fullUrl: String): OutboxPage

    /**
     * Descarga un JSON genérico desde una URL completa.
     */
    @GET
    suspend fun getRawJson(@Url fullUrl: String): ResponseBody

    /**
     * Resuelve un identificador de libro remoto para agregarlo a la base de datos local de la instancia.
     */
    @FormUrlEncoded
    @POST("resolve-book/")
    suspend fun resolveBook(
        @Field("remote_id") remoteId: String
    ): retrofit2.Response<ResponseBody>

    // BookWyrm expone búsqueda JSON con &format=json desde v0.6.x.
    // Devolvemos ResponseBody para poder procesar la respuesta en HTML o JSON
    // y extraer tanto libros locales como remotos.
    /**
     * Realiza una búsqueda de libros por título o autor.
     */
    @GET("search")
    @Headers("Accept: text/html")
    suspend fun searchBooks(
        @Query("q") query: String
    ): retrofit2.Response<ResponseBody>

    /**
     * Descarga los detalles de un libro (ActivityPub Object).
     */
    @GET
    suspend fun getBookDetails(@Url fullUrl: String): BookWyrmBookDetails

    @GET
    suspend fun getShelfData(@Url fullUrl: String): ShelfPage

    // Colección de usuarios seguidos por el usuario
    /**
     * Obtiene la lista de cuentas a las que sigue un usuario.
     */
    @GET
    suspend fun getFollowingData(@Url fullUrl: String): FollowingPage

    // Colección de seguidores del usuario (misma estructura que following)
    /**
     * Obtiene la lista de cuentas que siguen a un usuario.
     */
    @GET
    suspend fun getFollowersData(@Url fullUrl: String): FollowingPage

    // Seguir a un usuario: POST /follow/ con campo "user" = "@handle@instance"
    /**
     * Envía una petición para seguir a un usuario remoto o local.
     */
    @FormUrlEncoded
    @POST("follow/")
    suspend fun followUser(
        @Field("user") userHandle: String
    ): retrofit2.Response<ResponseBody>

    // Dejar de seguir: POST /unfollow/ con campo "user" = "@handle@instance"
    /**
     * Envía una petición para dejar de seguir a un usuario.
     */
    @FormUrlEncoded
    @POST("unfollow/")
    suspend fun unfollowUser(
        @Field("user") userHandle: String
    ): retrofit2.Response<ResponseBody>

    // POST /reading-status/<status>/<book_id>/ — BookWyrm espera el estado
    // y el ID del libro en la ruta, NO como campos de formulario.
    // Statuses válidos: "want", "start", "finish", "stop"
    /**
     * Actualiza el estado de lectura (Reading, Read, To-Read) de un libro.
     */
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
    /**
     * Actualiza el progreso de lectura (páginas leídas) de un libro.
     */
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

    /**
     * Publica una reseña (review) de un libro.
     */
    @FormUrlEncoded
    @POST("post/review/")
    suspend fun postReview(
        @Field("book") book: String,
        @Field("user") user: String,
        @Field("name") name: String?,
        @Field("content") content: String,
        @Field("rating") rating: String?,
        @Field("privacy") privacy: String,
        @Field("content_warning") contentWarning: String?,
        @Field("sensitive") sensitive: String?
    ): retrofit2.Response<okhttp3.ResponseBody>

    /**
     * Publica una cita (quote) de un libro.
     */
    @FormUrlEncoded
    @POST("post/quotation/")
    suspend fun postQuotation(
        @Field("book") book: String,
        @Field("user") user: String,
        @Field("quote") quote: String,
        @Field("content") content: String,
        @Field("privacy") privacy: String,
        @Field("content_warning") contentWarning: String?,
        @Field("sensitive") sensitive: String?
    ): retrofit2.Response<okhttp3.ResponseBody>

    /**
     * Asigna o actualiza la puntuación (estrellas) de un libro.
     */
    @FormUrlEncoded
    @POST("post/rating/")
    suspend fun postReviewRating(
        @Field("book") book: String,
        @Field("user") user: String,
        @Field("rating") rating: String?,
        @Field("privacy") privacy: String
    ): retrofit2.Response<okhttp3.ResponseBody>


    /**
     * Obtiene las preferencias de configuración del perfil del usuario logueado.
     */
    @GET("preferences/profile/")
    suspend fun getProfilePreferences(): retrofit2.Response<okhttp3.ResponseBody>

    @POST("preferences/profile/")
    suspend fun submitProfilePreferences(
        @Body body: okhttp3.RequestBody
    ): retrofit2.Response<okhttp3.ResponseBody>


    /**
     * Añade un libro a una estantería específica (custom shelf).
     */
    @FormUrlEncoded
    @POST("shelve/")
    suspend fun shelveBook(
        // BookWyrm espera el ID numérico del libro en el campo "book".
        // El campo "book_id" no existe en la vista Django y se ignora.
        @Field("book") bookId: String,
        @Field("shelf") shelfType: String
    ): retrofit2.Response<ResponseBody>


    // POST /favorite/<status_id>
    /**
     * Marca una actividad o estado como favorito (Like).
     */
    @Headers("Accept: application/json")
    @POST("favorite/{statusId}/")
    suspend fun favoriteStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>

    // POST /unfavorite/<status_id>
    /**
     * Quita la marca de favorito de una actividad.
     */
    @Headers("Accept: application/json")
    @POST("unfavorite/{statusId}/")
    suspend fun unfavoriteStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>

    // POST /post/reply/
    /**
     * Envía una respuesta a un estado o publicación existente.
     */
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
    /**
     * Crea una nueva publicación o estado (Post).
     */
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
    /**
     * Obtiene datos genéricos de una colección de ActivityPub.
     */
    @GET
    suspend fun getCollectionData(@Url fullUrl: String): ActivityPubCollection

    // NUEVO METODO PARA OBTENER HTML PURO
    /**
     * Descarga el código fuente HTML bruto de una página (usado para web scraping).
     */
    @GET
    suspend fun getRawHtml(@Url fullUrl: String): retrofit2.Response<okhttp3.ResponseBody>

    // Anotación corregida: @Url puro, requiere pasar la dirección absoluta desde el UI
    /**
     * Descarga el HTML bruto devolviendo el objeto Response de Retrofit.
     */
    @GET
    @Headers("Accept: text/html")
    suspend fun getRawHtmlResponse(@Url fullUrl: String): retrofit2.Response<ResponseBody>

    /**
     * Limpia el historial de notificaciones leídas.
     */
    @FormUrlEncoded
    @POST("notifications")
    suspend fun postClearNotifications(@Field("csrfmiddlewaretoken") csrfToken: String): retrofit2.Response<ResponseBody>

    /**
     * Envía una actividad (ej. Announce para Boost) directamente a la bandeja de salida (Outbox).
     */
    @Headers("Accept: application/activity+json", "Content-Type: application/activity+json")
    @POST
    suspend fun postToOutbox(
        @Url outboxUrl: String,
        @Body body: com.google.gson.JsonObject
    ): retrofit2.Response<ResponseBody>
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

            var response = chain.proceed(requestBuilder.build())

            // Handle 307 and 308 redirects manually, since followRedirects(false) is set globally.
            // These status codes MUST preserve the request method and body (unlike 301/302).
            // This prevents "307 Temporary Redirect" errors when HTTP->HTTPS upgrades happen 
            // or when reverse proxies enforce canonical URLs.
            var followCount = 0
            while ((response.code == 307 || response.code == 308) && followCount < 3) {
                val location = response.header("Location") ?: break
                val newUrl = response.request.url.resolve(location) ?: break
                
                val newRequest = response.request.newBuilder().url(newUrl).build()
                response.close()
                response = chain.proceed(newRequest)
                followCount++
            }

            response
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

    /**
     * Contexto temporal utilizado al actualizar el progreso de lectura.
     */
    data class ProgressContext(
        val readthroughId: String,
        val userId: String,
        val localBookId: String
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

            val userRegex = """name=["']user["']\s+value=["'](\d+)["']""".toRegex()
            val userMatch = userRegex.find(html)
            val userId = userMatch?.groupValues?.get(1)

            if (userId != null) {
                ReviewContext(userId, bookId)
            } else null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Resuelve la URL del actor de una reseña a su handle de seguimiento (@usuario@instancia).
     *
     * El enlace del autor raspado del HTML puede ser relativo (usuario local) o absoluto
     * (usuario federado). Se descarga el perfil ActivityPub para obtener el preferredUsername
     * y el host reales, que es lo que esperan los endpoints follow/unfollow.
     *
     * @param actorUrl URL del actor extraída de la reseña (relativa o absoluta).
     * @param instanceHostUrl Host de la instancia (ej. "https://bookwyrm.social") para resolver URLs relativas.
     * @return El handle "@usuario@host" o null si no se pudo resolver.
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    /**
     * Raspa el HTML del feed principal autenticado de BookWyrm (la home page que ve
     * el usuario en el navegador) y extrae las actividades del timeline.
     *
     * BookWyrm no expone una API REST para el feed "following". El verdadero timeline
     * se construye en el servidor con Redis + Django ORM y solo es accesible como HTML
     * renderizado. Este método parsea ese HTML usando JSoup para extraer todas las
     * actividades visibles: reviews, comentarios, citas, boosts, cambios de estantería, etc.
     *
     * @param api Cliente autenticado de BookWyrm.
     * @param instanceUrl URL base de la instancia (ej. "https://bookwyrm.social").
     * @param maxPages Número máximo de páginas del feed a cargar (cada página ≈ 20-30 items).
     * @return Lista de [com.ferlagod.rocinante.data.model.TimelineUiItem] lista para mostrar.
     */
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
                val t = System.currentTimeMillis()
                val feedUrl = if (page == 1) "${baseUrl}?t=$t" else "${baseUrl}?page=$page&t=$t"
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

        // Deduplicar por ID (mantiene el orden cronológico del HTML que viene ordenado del servidor)
        allItems.distinctBy { it.id }
    }

    /**
     * Obtiene el HTML de una URL siguiendo redirecciones manuales (OkHttp
     * tiene followRedirects=false para capturar 302 de Django).
     */
    private suspend fun fetchHtmlWithRedirects(
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

    /**
     * Parsea el documento HTML del feed de BookWyrm y extrae los status cards.
     *
     * BookWyrm usa Bulma CSS. Los status se renderizan como bloques dentro del
     * contenido principal del feed. Cada status tiene:
     * - Avatar del autor (img dentro de .media-left o similar)
     * - Nombre del autor y enlace a su perfil
     * - Tipo de actividad (review, comment, quotation, boost, shelve)
     * - Contenido textual
     * - Portada del libro si aplica
     * - Fecha de publicación (elemento <time>)
     * - Enlace permanente al status (para likes/replies)
     */
    private fun parseStatusCards(
        document: org.jsoup.nodes.Document,
        baseUrl: String
    ): List<com.ferlagod.rocinante.data.model.TimelineUiItem> {
        val items = mutableListOf<com.ferlagod.rocinante.data.model.TimelineUiItem>()
        val cleanBase = baseUrl.trimEnd('/')

        // BookWyrm renderiza cada status dentro de un <div> con data-date o dentro de
        // article elements, o blocks con class "block" dentro del feed principal.
        // Usamos múltiples selectores para cubrir diferentes versiones de BookWyrm.
        var elements = document.select("#feed > .block, .column.is-two-thirds > .block")
        
        if (elements.isEmpty()) {
            val statusElements = document.select(
                "[data-date], .block[data-id], .is-flex.is-align-items-stretch"
            )
            elements = if (statusElements.isEmpty()) document.select(".block:has(time):has(img)") else statusElements
        }

        for (element in elements) {
            try {
                // --- Extraer la fecha de publicación ---
                val timeElement = element.selectFirst("time")
                // BookWyrm a veces no usa <time>, sino una etiqueta <a> dentro del breadcrumb
                val breadcrumbDate = element.selectFirst(".breadcrumb li a")?.text() ?: ""
                val publishedDate = timeElement?.attr("datetime")?.takeIf { it.isNotBlank() } 
                    ?: timeElement?.text()?.takeIf { it.isNotBlank() } 
                    ?: breadcrumbDate

                // --- Extraer el enlace permanente (ID del status) ---
                val permalinkElement = element.select("a").firstOrNull { a ->
                    val href = a.attr("href")
                    href.contains("/status/") || href.contains("/review/") ||
                    href.contains("/comment/") || href.contains("/quotation/") ||
                    href.contains("/reviewrating/")
                }
                val statusId = permalinkElement?.attr("href")?.let { href ->
                    resolveUrl(href, cleanBase)
                } ?: "scraped-${publishedDate.hashCode()}-${items.size}"

                // --- Extraer el actor (autor) ---
                val avatarImg = element.selectFirst(".media-left img")
                    ?: element.selectFirst("img.avatar")
                    ?: element.selectFirst("img[src*=avatar]")
                val avatarSrc = avatarImg?.attr("src") ?: ""
                val avatarUrl = resolveUrl(avatarSrc, cleanBase)

                // BookWyrm especifica al autor usando schema.org Person
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

                // --- Determinar el tipo de actividad ---
                val statusType = detectStatusType(element, statusId)

                // --- Extraer el contenido ---
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
                        commentClone.select("blockquote").remove() // Quitar la cita del comentario si estuviera anidada
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

                // Extraer el título de la review si existe
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

                // --- Para actividades de boost/shelve sin contenido propio ---
                val headerText = element.selectFirst(".status-info")?.text()
                    ?: element.selectFirst(".card-header-title")?.text()
                    ?: ""
                val displayContent = if (cleanContent.isBlank() && headerText.isNotBlank()) {
                    com.ferlagod.rocinante.utils.HtmlUtils.stripHtml(headerText)
                } else {
                    cleanContent
                }

                // --- Extraer portada del libro ---
                val coverImg = element.selectFirst("img[src*=covers]")
                    ?: element.selectFirst("img[alt*=cover]")
                    ?: element.selectFirst(".book-cover img")
                    ?: element.selectFirst(".cover-container img")
                val coverSrc = coverImg?.attr("src") ?: ""
                val bookCoverUrl = resolveUrl(coverSrc, cleanBase).takeIf { it.isNotEmpty() }

                // --- Extraer enlace al libro ---
                val bookLink = element.selectFirst("a[href*='/book/']")
                val bookUrl = bookLink?.attr("href")?.let { href ->
                    resolveUrl(href, cleanBase)
                }

                // --- Extraer el ID local de BookWyrm (para dar Like/Responder a status remotos) ---
                var localId: String? = null
                val favForm = element.selectFirst("form[action*=/favorite/], form[action*=/unfavorite/]")
                if (favForm != null) {
                    val action = favForm.attr("action")
                    val match = """/(favorite|unfavorite)/(\d+)""".toRegex().find(action)
                    if (match != null) {
                        localId = match.groupValues[2]
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

                // --- Construir el TimelineUiItem ---
                val item = com.ferlagod.rocinante.data.model.TimelineUiItem(
                    id = statusId,
                    type = statusType,
                    published = publishedDate,
                    content = displayContent.ifBlank { "Sin contenido" },
                    bookCoverUrl = bookCoverUrl,
                    bookUrl = bookUrl,
                    actorName = actorName.ifBlank { "Usuario" },
                    actorAvatarUrl = avatarUrl.takeIf { it.isNotEmpty() },
                    objectId = localId ?: statusId
                )
                
                if (item.actorName == "Usuario" && item.content == "Sin contenido" && item.bookCoverUrl.isNullOrEmpty()) {
                    continue // Ignorar bloques vacíos sin contenido (ej. mensaje de "Fin del feed")
                }
                
                items.add(item)
            } catch (_: Exception) {
                // Saltar status mal formados
                continue
            }
        }

        return items
    }

    /**
     * Detecta el tipo de status (Review, Comment, Quotation, Note, Announce, Add)
     * a partir del HTML del elemento.
     */
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

    /**
     * Resuelve una URL relativa en absoluta usando la base de la instancia.
     */
    private fun resolveUrl(src: String, cleanBase: String): String {
        if (src.isEmpty()) return ""
        return if (src.startsWith("http")) src else {
            val base = if (cleanBase.endsWith("/")) cleanBase.dropLast(1) else cleanBase
            val path = if (src.startsWith("/")) src else "/$src"
            "$base$path"
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extrae el contador actual de notificaciones no leídas haciendo scraping del HTML de la página de inicio.
     * Es una operación ligera diseñada para ejecutarse periódicamente en segundo plano (Foreground Polling).
     *
     * @param api Cliente de red para mantener la cookie de sesión.
     * @param instanceUrl URL de la instancia actual.
     * @return Número entero con el conteo de notificaciones no leídas, o 0 en caso de error/no haber notificaciones.
     */
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

    /**
     * Realiza un scraping completo de la página de notificaciones de la cuenta (`/notifications`)
     * y extrae una lista estructurada de las notificaciones recientes.
     * Utiliza expresiones regulares simples sobre el texto crudo para inferir el tipo de notificación.
     *
     * @param api Cliente de red para la petición autenticada.
     * @param instanceUrl URL de la instancia actual.
     * @return Lista parseada de [NotificationUiItem] lista para pintar en la interfaz.
     */
    suspend fun scrapeNotifications(api: BookWyrmApi, instanceUrl: String): List<NotificationUiItem> = withContext(Dispatchers.IO) {
        val notifications = mutableListOf<NotificationUiItem>()
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val notifUrl = "${baseUrl}notifications"
            
            val html = fetchHtmlWithRedirects(api, notifUrl, baseUrl)
            if (html.isEmpty()) return@withContext emptyList()
            
            val document = org.jsoup.Jsoup.parse(html)
            // BookWyrm uses div.notification for the wrapper
            val elements = document.select("div.notification:not(.live-message)")
            for (element in elements) {
                val id = element.attr("id").ifEmpty { java.util.UUID.randomUUID().toString() }
                val isUnread = element.hasClass("unread") // Assuming unread class is used
                
                // Content
                val contentHtml = element.select(".content").firstOrNull()?.html() ?: element.text()
                
                // Avatar
                val avatarImg = element.select("img").firstOrNull()
                var avatarUrl = avatarImg?.attr("src") ?: ""
                if (avatarUrl.isNotEmpty() && !avatarUrl.startsWith("http")) {
                    avatarUrl = "$baseUrl${avatarUrl.trimStart('/')}"
                }
                
                // Actor name (first bold/link or just something)
                val actorName = element.select("strong").firstOrNull()?.text() 
                    ?: element.select("a").firstOrNull()?.text() ?: "Unknown"
                    
                // Date
                val timeElement = element.select("time").firstOrNull()
                val date = timeElement?.text() ?: ""
                
                // Permalink
                val permalink = element.select("a.time, .status-link").firstOrNull()?.attr("href")?.let {
                    if (it.startsWith("http")) it else "$baseUrl${it.trimStart('/')}"
                }
                
                // Guess type based on text
                val fullText = element.text().lowercase()
                val type = when {
                    fullText.contains("replied") || fullText.contains("respondió") -> NotificationType.REPLY
                    fullText.contains("mention") || fullText.contains("mencionó") -> NotificationType.MENTION
                    fullText.contains("favorite") || fullText.contains("favorito") || fullText.contains("gusta") -> NotificationType.FAVORITE
                    fullText.contains("boost") || fullText.contains("compartió") -> NotificationType.BOOST
                    fullText.contains("follow") || fullText.contains("siguió") || fullText.contains("sigue") -> NotificationType.FOLLOW
                    else -> NotificationType.UNKNOWN
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
     * Limpia (borra) todas las notificaciones leídas de la cuenta realizando un POST a /notifications.
     * @param api Cliente de red autenticado.
     * @param instanceUrl URL de la instancia.
     * @return true si la operación tuvo éxito.
     */
    suspend fun clearNotifications(api: BookWyrmApi, instanceUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val notifUrl = "${baseUrl}notifications"
            
            val html = fetchHtmlWithRedirects(api, notifUrl, baseUrl)
            if (html.isEmpty()) return@withContext false
            val csrfToken = org.jsoup.Jsoup.parse(html).select("input[name=csrfmiddlewaretoken]").attr("value")
            
            if (csrfToken.isEmpty()) return@withContext false
            
            val response = api.postClearNotifications(csrfToken)
            response.isSuccessful
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            false
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