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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSessionStorage(@ApplicationContext context: Context): SessionStorage {
        return SessionStorage(context)
    }

    @Provides
    @Singleton
    fun provideSettingsPreferences(@ApplicationContext context: Context): SettingsPreferences {
        return SettingsPreferences(context)
    }

    @Provides
    @Singleton
    fun provideTimelineCache(@ApplicationContext context: Context): TimelineCache {
        return TimelineCache(context)
    }

    @Provides
    @Singleton
    fun provideFollowListCache(@ApplicationContext context: Context): FollowListCache {
        return FollowListCache(context)
    }

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
            // Force cache for 2 minutes for JSON and ActivityPub responses
            if (contentType?.subtype?.contains("json") == true || contentType?.subtype?.contains("activity+json") == true) {
                val cacheControl = okhttp3.CacheControl.Builder()
                    .maxAge(2, java.util.concurrent.TimeUnit.MINUTES)
                    .build()
                response = response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", cacheControl.toString())
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

    @Provides
    @Singleton
    fun provideUserRepository(api: BookWyrmApi): UserRepository {
        return UserRepository(api, java.util.concurrent.ConcurrentHashMap())
    }

    @Provides
    @Singleton
    fun provideTimelineRepository(api: BookWyrmApi, userRepository: UserRepository): TimelineRepository {
        return TimelineRepository(api, userRepository)
    }

    @Provides
    @Singleton
    fun provideInteractionRepository(api: BookWyrmApi): InteractionRepository {
        return InteractionRepository(api)
    }

    @Provides
    @Singleton
    fun provideSearchRepository(api: BookWyrmApi): SearchRepository {
        return SearchRepository(api)
    }
}
