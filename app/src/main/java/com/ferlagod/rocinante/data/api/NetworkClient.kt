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


import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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

    /**
     * Mezcla una cadena "nombre=valor; ..." recién obtenida del WebView. Se usa al renovar
     * la autorización de Anubis, para que el tarro deje de reenviar la cookie caducada.
     * No borra las que no vengan en la cadena, porque el WebView puede no exponerlas todas.
     */
    fun merge(cookieString: String) {
        cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    cookieMap[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
                }
            }
    }

    /** Las cookies actuales como cadena "nombre=valor; ...". */
    fun asCookieString(): String = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

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

    /**
     * User-Agent que se envía en TODAS las peticiones HTTP. Debe coincidir con el del
     * WebView de login, porque es a ese UA al que la instancia (y protecciones tipo
     * Anubis) emitió la cookie de sesión/clearance; un UA distinto al reproducir esa
     * cookie puede rechazarse. [RocinanteApplication] lo inicializa con el UA real del
     * WebView del sistema más el sufijo "Rocinante/<versión>"; hasta entonces sirve
     * este valor de respaldo, que también se usa si el WebView no está disponible.
     */
    @Volatile
    var userAgent: String =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 Rocinante/1.0"

    /**
     * Valor actual de la cookie csrftoken (el secreto que se enviará como Cookie).
     * Enviarlo como campo de formulario garantiza que el token coincida con la cookie,
     * evitando el 403 que produce el token enmascarado extraído del HTML.
     */
    fun currentCsrfToken(): String? =
        (lastOkHttpClient?.cookieJar as? SessionCookieJar)?.currentCsrfToken()

    /**
     * Mete en el tarro de cookies vivo una cadena recién obtenida del WebView, para que las
     * siguientes peticiones dejen de reenviar la autorización de Anubis caducada.
     */
    fun replaceCookies(cookieString: String) {
        (lastOkHttpClient?.cookieJar as? SessionCookieJar)?.merge(cookieString)
    }

    fun createAuthenticatedApi(baseUrl: String, cookieString: String): BookWyrmApi {
        val cleanUrl = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
        val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        val host = finalUrl.toHttpUrlOrNull()?.host ?: cleanUrl
        // Referer con host normalizado en minúsculas. Django compara el Referer contra
        // el host de la petición de forma sensible a mayúsculas (is_same_domain solo
        // pasa el patrón a minúsculas, no el host del Referer); un "BookWyrm.social"
        // frente a "bookwyrm.social" hace fallar la verificación CSRF con un 403.
        val refererHeader = finalUrl.toHttpUrlOrNull()?.toString() ?: finalUrl

        // CookieJar con las cookies de sesión iniciales
        val cookieJar = SessionCookieJar(cookieString, host)

        val interceptor = Interceptor { chain ->
            // Leer el CSRF más reciente del jar (puede haber rotado tras un POST previo)
            val csrfToken = cookieJar.currentCsrfToken()

            val requestBuilder = chain.request().newBuilder()
                .addHeader("Referer", refererHeader)
                .addHeader("User-Agent", userAgent)

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

            // Anubis: cuando su cookie de paso caduca, la instancia responde a todo con un
            // 307 hacia el reto. Seguirlo devolvería la página "no eres un bot" como si fuese
            // la respuesta pedida. En vez de eso se resuelve el reto y se repite la petición
            // una sola vez; si no se consigue, se deja pasar el 307 y falla como antes.
            if (AnubisClearance.isChallenge(response)) {
                val fresh = AnubisClearance.refreshBlocking(finalUrl, cookieJar.asCookieString())
                if (fresh != null) {
                    response.close()
                    response = chain.proceed(requestBuilder.build())
                }
            }

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

        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookWyrmApi::class.java)
    }
}