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

import com.ferlagod.rocinante.data.model.*
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.Headers

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
        @Path("username") username: String
    ): BookWyrmProfile

    /**
     * Obtiene el perfil extendido (Outbox, colecciones) de un usuario.
     */
    @GET
    suspend fun getFullUserProfile(
        @Url fullUrl: String
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
        @Field("content_warning") contentWarning: String = "",
        // Token CSRF de formulario (además de la cabecera X-CSRFToken). Necesario en
        // instancias Django donde la cabecera por sí sola no supera la verificación CSRF.
        @Field("csrfmiddlewaretoken") csrfToken: String = ""
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

    // POST /boost/<status_id>
    /**
     * Difunde (Boost/Announce) una actividad.
     */
    @Headers("Accept: application/json")
    @POST("boost/{statusId}/")
    suspend fun boostStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>

    // POST /unboost/<status_id>
    /**
     * Deshace la difusión de una actividad.
     */
    @Headers("Accept: application/json")
    @POST("unboost/{statusId}/")
    suspend fun unboostStatus(@Path("statusId") statusId: String): retrofit2.Response<okhttp3.ResponseBody>



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
