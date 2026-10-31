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
package com.ferlagod.rocinante.di

import android.content.Context
import com.ferlagod.rocinante.data.api.AnubisClearance
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.local.SessionStorage
import com.ferlagod.rocinante.data.repository.InteractionRepository
import com.ferlagod.rocinante.data.repository.SearchRepository
import com.ferlagod.rocinante.data.repository.TimelineRepository
import com.ferlagod.rocinante.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Singleton

import com.ferlagod.rocinante.data.local.SettingsPreferences
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.local.FollowListCache
import kotlinx.coroutines.launch

/**
 * Módulo de inyección de dependencias principal de la aplicación mediante Hilt.
 *
 * Este módulo está instalado en el componente [SingletonComponent], lo que significa
 * que todas las dependencias proporcionadas aquí tendrán un ciclo de vida único
 * (Singleton) durante la ejecución de la aplicación.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Proporciona la instancia singleton de [SessionStorage] para la gestión de la sesión
     * del usuario activo (URL de la instancia, cookies y token CSRF).
     */
    @Provides
    @Singleton
    fun provideSessionStorage(@ApplicationContext context: Context): SessionStorage {
        return SessionStorage(context)
    }

    /**
     * Proporciona la instancia singleton de [SettingsPreferences] para acceder a las
     * preferencias configuradas por el usuario a través de DataStore.
     */
    @Provides
    @Singleton
    fun provideSettingsPreferences(@ApplicationContext context: Context): SettingsPreferences {
        return SettingsPreferences(context)
    }

    /**
     * Proporciona la instancia singleton de [TimelineCache] para almacenar y recuperar
     * temporalmente las publicaciones de la línea de tiempo de forma local.
     */
    @Provides
    @Singleton
    fun provideTimelineCache(@ApplicationContext context: Context): TimelineCache {
        return TimelineCache(context)
    }

    /**
     * Proporciona la instancia singleton de [FollowListCache] para la gestión en caché de
     * listas de seguimiento (seguidores y seguidos).
     */
    @Provides
    @Singleton
    fun provideFollowListCache(@ApplicationContext context: Context): FollowListCache {
        return FollowListCache(context)
    }

    /**
     * Configura y proporciona la interfaz del cliente API [BookWyrmApi] mediante Retrofit.
     *
     * Incluye interceptores personalizados en el cliente OkHttp:
     * - **Interceptor de sesión activa**: Sustituye dinámicamente la URL base estática por el
     *   dominio real de la instancia del usuario, adjunta encabezados obligatorios (`Referer`,
     *   `User-Agent`, `Cookie`, `X-CSRFToken`) y gestiona manualmente redirecciones HTTP 307 y 308.
     * - **Interceptor BOM**: Limpia el carácter invisible UTF-8 BOM (`\uFEFF`) si está presente en las respuestas JSON.
     * - **Caché HTTP**: Almacena respuestas JSON/HTML en el almacenamiento local para optimizar las peticiones de red.
     */
    @Provides
    @Singleton
    fun provideBookWyrmApi(sessionStorage: SessionStorage, @ApplicationContext context: Context): BookWyrmApi {
        // Deja a AnubisClearance lo que necesita para renovar desde dentro de un interceptor,
        // que no puede recibir inyección: el contexto para el WebView y la sesión que guardar.
        AnubisClearance.init(context, sessionStorage)

        // Host de la instancia con la que se ha iniciado sesión; "" si aún no hay sesión, y
        // entonces el tarro no manda cookies a nadie.
        val instanceHostOf: (com.ferlagod.rocinante.data.model.SessionData) -> String = {
            val clean = if (it.instanceUrl.startsWith("http")) it.instanceUrl else "https://${it.instanceUrl}"
            clean.toHttpUrlOrNull()?.host?.lowercase() ?: clean.lowercase()
        }
        val hostProvider: () -> String = {
            sessionStorage.currentSession?.let(instanceHostOf)
                ?: NetworkClient.lastInstanceHost
                ?: ""
        }

        val initialCookies = sessionStorage.currentSession?.cookie.orEmpty()
        val cookieJar = com.ferlagod.rocinante.data.api.SessionCookieJar(initialCookies, hostProvider) { newCookies ->
            val sess = sessionStorage.currentSession
            if (sess != null && sess.cookie != newCookies) {
                // Se fija en memoria ya, para que la siguiente petición no reponga la cadena vieja,
                // y se escribe en disco aparte.
                val updated = sess.copy(cookie = newCookies)
                sessionStorage.currentSession = updated
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val latest = sessionStorage.currentSession
                        if (latest != null && latest.instanceUrl == updated.instanceUrl && latest.username == updated.username) {
                            sessionStorage.saveSession(latest)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // We create a singleton Retrofit instance using a base URL placeholder.
        // An interceptor reads the active session from SessionStorage dynamically.
        val interceptor = okhttp3.Interceptor { chain ->
            val session = sessionStorage.loadedSession()
            val request = chain.request()
            val requestBuilder = request.newBuilder()

            var isToInstance = false

            if (session != null) {
                val cleanUrl = if (session.instanceUrl.startsWith("http")) session.instanceUrl else "https://${session.instanceUrl}"
                val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
                val hostUrl = finalUrl.toHttpUrlOrNull()
                val host = hostUrl?.host?.lowercase() ?: cleanUrl.lowercase()
                NetworkClient.lastInstanceHost = host
                val scheme = hostUrl?.scheme ?: "https"
                val origin = "$scheme://$host"
                val referer = "$origin/"

                // Solo reescribir si la URL apunta al host placeholder de Retrofit ("bookwyrm.social").
                // Si va a una URL remota federada (otra instancia, OpenLibrary, etc.), NO alteramos el host.
                if (request.url.host.equals("bookwyrm.social", ignoreCase = true) && hostUrl != null) {
                    val newUrl = request.url.newBuilder()
                        .scheme(hostUrl.scheme)
                        .host(hostUrl.host)
                        .port(hostUrl.port)
                        .build()
                    requestBuilder.url(newUrl)
                    isToInstance = true
                } else if (NetworkClient.isInstanceHost(request.url.host, host)) {
                    isToInstance = true
                }

                if (isToInstance) {
                    requestBuilder.header("Origin", origin)
                    requestBuilder.header("Referer", referer)
                }

                requestBuilder.header("User-Agent", NetworkClient.userAgent)

                val acceptHeader = request.header("Accept")
                if (acceptHeader?.contains("text/html") != true) {
                    requestBuilder.header("X-Requested-With", "XMLHttpRequest")
                }
                if (acceptHeader == null) {
                    requestBuilder.header("Accept", "application/activity+json, application/json")
                }

                // Sincronizar cookies y host con la sesión activa
                cookieJar.syncSession(host, session.cookie)

                if (isToInstance) {
                    val csrf = cookieJar.currentCsrfToken()
                        ?: "csrftoken=([^;]+)".toRegex().find(session.cookie)?.groupValues?.get(1)
                    if (!csrf.isNullOrBlank()) {
                        requestBuilder.header("X-CSRFToken", csrf)
                        NetworkClient.formWithCsrf(request.body, csrf)?.let { requestBuilder.method(request.method, it) }
                    }
                }
            } else {
                requestBuilder.header("User-Agent", NetworkClient.userAgent)
            }

            var response = chain.proceed(requestBuilder.build())

            // Anubis: renovación automática ante 307 hacia el reto o 403 de protección. Solo si
            // es la propia instancia: el reto de otro servidor no se arregla renovando esta cookie.
            if (session != null && isToInstance && AnubisClearance.isChallenge(response)) {
                val fresh = AnubisClearance.refreshBlocking(session.instanceUrl, cookieJar.asCookieString())
                if (fresh != null) {
                    cookieJar.merge(fresh)
                    response.close()
                    val csrf = cookieJar.currentCsrfToken()
                    if (csrf != null) {
                        requestBuilder.header("X-CSRFToken", csrf)
                        NetworkClient.formWithCsrf(request.body, csrf)?.let { requestBuilder.method(request.method, it) }
                    }
                    response = chain.proceed(requestBuilder.build())
                }
            }

            // Handle 307 and 308 redirects manually, avoiding challenge paths
            var followCount = 0
            while ((response.code == 307 || response.code == 308) && followCount < 3) {
                val location = response.header("Location") ?: break
                if (location.contains(AnubisClearance.CHALLENGE_PATH)) break
                val newUrl = response.request.url.resolve(location) ?: break
                
                val newRequest = response.request.newBuilder().url(newUrl).build()
                response.close()
                response = chain.proceed(newRequest)
                followCount++
            }

            // Sesión caducada: la instancia contesta 401 o, como hace Django, redirige a /login.
            // Solo cuenta la propia instancia; un 401 de un servidor federado es cosa suya.
            if (session != null && isToInstance && (response.code == 401 || NetworkClient.isLoginRedirect(response))) {
                com.ferlagod.rocinante.utils.NetworkErrors.reportSessionExpired()
            }
            response
        }

        val bomInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            if (body != null) {
                val contentType = body.contentType()
                if (contentType?.subtype?.contains("json") == true) {
                    val source = body.source()
                    // UTF-8 BOM bytes: 0xEF, 0xBB, 0xBF
                    if (source.request(3) &&
                        source.buffer[0] == 0xEF.toByte() &&
                        source.buffer[1] == 0xBB.toByte() &&
                        source.buffer[2] == 0xBF.toByte()
                    ) {
                        source.skip(3)
                        // Del source, no del buffer: el buffer solo tiene lo leído hasta ahora
                        // y cortaba los JSON largos.
                        val cleanJson = source.readUtf8()
                        val newBody = cleanJson.toResponseBody(contentType)
                        return@Interceptor response.newBuilder().body(newBody).build()
                    }
                }
            }
            response
        }

        val cacheSize = 15L * 1024 * 1024
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), cacheSize)

        // Solo JSON e imágenes. Las páginas HTML de libros ya no se fuerzan a caché: llevan el
        // estado de lectura, las lecturas y los tokens del usuario, y servir durante 10 minutos
        // la copia de antes de cambiar de estantería o terminar un libro deshacía en pantalla
        // lo que se acababa de hacer.
        val cacheInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            if (request.method == "GET" && response.isSuccessful) {
                val urlPath = request.url.encodedPath
                if (urlPath.contains("/book/") || urlPath.contains("/author/") || urlPath.contains("/edition/") || urlPath.contains("/images/")) {
                    val contentType = response.body?.contentType()
                    if (contentType?.subtype?.contains("json") == true || contentType?.type?.contains("image") == true) {
                        response = response.newBuilder()
                            .removeHeader("Pragma")
                            .removeHeader("Cache-Control")
                            .header("Cache-Control", "public, max-age=600")
                            .build()
                    }
                }
            }
            response
        }

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
            .cache(cache)
            .cookieJar(cookieJar)
            .addInterceptor(interceptor)
            .addInterceptor(bomInterceptor)
            .addNetworkInterceptor(cacheInterceptor)
            .followRedirects(false)
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        NetworkClient.lastOkHttpClient = okHttpClient
        NetworkClient.lastInstanceHost = hostProvider().ifBlank { null }

        val api = Retrofit.Builder()
            .baseUrl("https://bookwyrm.social/") // Placeholder, overwritten by interceptor
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(NetworkClient.gson))
            .build()
            .create(BookWyrmApi::class.java)

        // Las pantallas que aún piden su propia API con createAuthenticatedApi reciben esta.
        // Lectura sin bloquear: se consulta desde la composición, en el hilo principal.
        NetworkClient.sharedApiHostProvider = { sessionStorage.currentSession?.let(instanceHostOf) }
        NetworkClient.sharedApi = api
        return api
    }

    /**
     * Proporciona la instancia singleton del repositorio [UserRepository] para gestionar perfiles
     * de usuario y autores con caché concurrente en memoria.
     */
    @Provides
    @Singleton
    fun provideUserRepository(api: BookWyrmApi): UserRepository {
        return UserRepository(api, java.util.concurrent.ConcurrentHashMap())
    }

    /**
     * Proporciona la instancia singleton del repositorio [TimelineRepository] encargado de
     * gestionar las líneas de tiempo y feeds de publicaciones.
     */
    @Provides
    @Singleton
    fun provideTimelineRepository(
        api: BookWyrmApi,
        userRepository: UserRepository,
        @ApplicationContext context: Context
    ): TimelineRepository {
        return TimelineRepository(api, userRepository, context)
    }

    /**
     * Proporciona la instancia singleton del repositorio [InteractionRepository] para gestionar
     * las interacciones del usuario (me gusta, compartidos, comentarios, cambios de estantes).
     */
    @Provides
    @Singleton
    fun provideInteractionRepository(api: BookWyrmApi): InteractionRepository {
        return InteractionRepository(api)
    }

    /**
     * Proporciona la instancia singleton del repositorio [SearchRepository] para realizar búsquedas
     * de libros, autores y usuarios en BookWyrm.
     */
    @Provides
    @Singleton
    fun provideSearchRepository(api: BookWyrmApi): SearchRepository {
        return SearchRepository(api)
    }
}
