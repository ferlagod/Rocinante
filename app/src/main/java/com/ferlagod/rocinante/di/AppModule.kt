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
import com.ferlagod.rocinante.data.api.BookWyrmApi
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
        // We create a singleton Retrofit instance using a base URL placeholder.
        // An interceptor reads the active session from SessionStorage dynamically.
        val interceptor = okhttp3.Interceptor { chain ->
            val session = sessionStorage.currentSession
            val requestBuilder = chain.request().newBuilder()

            if (session != null) {
                val cleanUrl = if (session.instanceUrl.startsWith("http")) session.instanceUrl else "https://${session.instanceUrl}"
                val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
                val hostUrl = finalUrl.toHttpUrlOrNull()

                if (hostUrl != null) {
                    val newUrl = chain.request().url.newBuilder()
                        .scheme(hostUrl.scheme)
                        .host(hostUrl.host)
                        .port(hostUrl.port)
                        .build()
                    requestBuilder.url(newUrl)
                }

                // Add authentication and standard headers (same as NetworkClient)
                requestBuilder.addHeader("Referer", finalUrl)
                requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 Rocinante/1.0")
                
                val acceptHeader = chain.request().header("Accept")
                if (acceptHeader?.contains("text/html") != true) {
                    requestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")
                }
                if (acceptHeader == null) {
                    requestBuilder.addHeader("Accept", "application/activity+json, application/json")
                }
                
                // Add cookies manually since we don't use CookieJar here
                requestBuilder.addHeader("Cookie", session.cookie)
                
                // Extract CSRF from cookie if possible
                val csrfMatch = "csrftoken=([^;]+)".toRegex().find(session.cookie)
                if (csrfMatch != null) {
                    requestBuilder.addHeader("X-CSRFToken", csrfMatch.groupValues[1])
                }
            }

            var response = chain.proceed(requestBuilder.build())

            // Handle 307 and 308 redirects manually
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

        val bomInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            if (body != null) {
                val contentType = body.contentType()
                if (contentType?.subtype?.contains("json") == true || contentType?.subtype?.contains("activity+json") == true) {
                    val rawJson = body.string()
                    if (rawJson.startsWith("\uFEFF")) {
                        val cleanJson = rawJson.substring(1)
                        val newBody = cleanJson.toResponseBody(contentType)
                        return@Interceptor response.newBuilder().body(newBody).build()
                    } else {
                        val newBody = rawJson.toResponseBody(contentType)
                        return@Interceptor response.newBuilder().body(newBody).build()
                    }
                }
            }
            response
        }

        val cacheSize = 15L * 1024 * 1024
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), cacheSize)

        val cacheInterceptor = okhttp3.Interceptor { chain ->
            var response = chain.proceed(chain.request())
            val contentType = response.body?.contentType()
            if (contentType?.subtype?.contains("json") == true || contentType?.subtype?.contains("html") == true || contentType?.subtype?.contains("activity+json") == true) {
                response = response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .removeHeader("Set-Cookie") // Prevent OkHttp from bypassing cache due to session cookies
                    .header("Cache-Control", "public, max-age=120")
                    .build()
            }
            response
        }

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(interceptor)
            .addInterceptor(bomInterceptor)
            .addNetworkInterceptor(cacheInterceptor)
            .followRedirects(false)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl("https://bookwyrm.social/") // Placeholder, overwritten by interceptor
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookWyrmApi::class.java)
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
    fun provideTimelineRepository(api: BookWyrmApi, userRepository: UserRepository): TimelineRepository {
        return TimelineRepository(api, userRepository)
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
