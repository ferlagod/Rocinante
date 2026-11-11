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


import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * CookieJar mutable que:
 * 1. Inicializa las cookies a partir del string de sesión guardado en DataStore.
 * 2. Actualiza automáticamente `csrftoken` con cada respuesta Set-Cookie del servidor.
 *    Esto es imprescindible para Django, que rota el token CSRF en cada respuesta POST.
 *
 * Las cookies son solo de la instancia del usuario: no se envían a ningún otro servidor ni
 * se aceptan de él. El tarro las guarda por nombre, sin dominio, así que una cookie
 * `csrftoken` de otra instancia BookWyrm (otro Django) pisaría la del usuario.
 */
class SessionCookieJar(
    initialCookieString: String,
    private val hostProvider: () -> String,
    private val onCookiesUpdated: ((String) -> Unit)? = null
) : CookieJar {

    constructor(
        initialCookieString: String,
        host: String,
        onCookiesUpdated: ((String) -> Unit)? = null
    ) : this(initialCookieString, { host }, onCookiesUpdated)

    // Mapa thread-safe nombre → valor; se actualiza con cada respuesta
    private val cookieMap: java.util.concurrent.ConcurrentHashMap<String, String> =
        java.util.concurrent.ConcurrentHashMap()

    @Volatile
    private var lastHost: String? = null

    /**
     * Última cadena de sesión que se ha volcado en el tarro con [syncSession]. Solo se vuelve
     * a leer cuando cambia: si no, cada petición reponía la cadena guardada, que va por detrás
     * del tarro mientras se escribe en disco, y deshacía la rotación de csrftoken recién recibida.
     */
    @Volatile
    private var lastSyncedCookie: String? = null

    init {
        parseCookies(initialCookieString)
    }

    private fun parseCookies(raw: String) {
        val reserved = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "priority")
        raw.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val key = part.substring(0, idx).trim()
                    val value = part.substring(idx + 1).trim()
                    if (key.isNotEmpty() && !reserved.contains(key.lowercase())) {
                        if (value.isEmpty()) {
                            cookieMap.remove(key)
                        } else {
                            cookieMap[key] = value
                        }
                    }
                }
            }
    }

    /** ¿Va la petición (o viene la respuesta) de la instancia del usuario o de un subdominio suyo? */
    private fun matchesHost(requestHost: String): Boolean =
        NetworkClient.isInstanceHost(requestHost, hostProvider())

    /** Devuelve el valor actual del csrftoken (puede haber rotado). */
    fun currentCsrfToken(): String? = cookieMap["csrftoken"]

    /**
     * Sincroniza las cookies y el host activo. Si la instancia cambió,
     * limpia las cookies de la instancia previa para evitar cruces.
     */
    fun syncSession(currentHost: String, cookieString: String) {
        val cleanH = NetworkClient.cleanHost(currentHost)
        if (lastHost != null && cleanH.isNotEmpty() && !lastHost.equals(cleanH, ignoreCase = true)) {
            cookieMap.clear()
            lastSyncedCookie = null
        }
        if (cleanH.isNotEmpty()) {
            lastHost = cleanH
        }
        if (cookieString.isNotBlank() && cookieString != lastSyncedCookie) {
            parseCookies(cookieString)
            lastSyncedCookie = cookieString
        }
    }

    /** Limpia todas las cookies y el host registrado. */
    fun clear() {
        cookieMap.clear()
        lastHost = null
        lastSyncedCookie = null
    }

    /**
     * Mezcla una cadena "nombre=valor; ..." recién obtenida del WebView o renovada.
     * No borra las que no vengan en la cadena, porque el WebView puede no exponerlas todas.
     */
    fun merge(cookieString: String) {
        parseCookies(cookieString)
        onCookiesUpdated?.invoke(asCookieString())
    }

    /** Las cookies actuales como cadena "nombre=valor; ...". */
    fun asCookieString(): String = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Solo guardar cookies si la respuesta proviene del host principal
        if (!matchesHost(url.host)) return
        if (cookies.isEmpty()) return
        
        var changed = false
        cookies.forEach { cookie ->
            if (cookie.value.isEmpty() || cookie.expiresAt < System.currentTimeMillis()) {
                if (cookieMap.remove(cookie.name) != null) changed = true
            } else {
                val prev = cookieMap.put(cookie.name, cookie.value)
                if (prev != cookie.value) changed = true
            }
        }
        if (changed) {
            onCookiesUpdated?.invoke(asCookieString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Solo enviar cookies si la petición se dirige al host principal
        if (!matchesHost(url.host)) return emptyList()
        
        return cookieMap.mapNotNull { (name, value) ->
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .build()
            } catch (_: Exception) {
                null
            }
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
    @Volatile
    var lastOkHttpClient: OkHttpClient? = null
        internal set

    /**
     * Host de la instancia con la que se ha iniciado sesión, en minúsculas. Sirve para saber
     * si un libro es de casa o viene de otra instancia, y ahorrarse así el resolverlo.
     */
    @Volatile
    var lastInstanceHost: String? = null
        internal set

    /**
     * La API de la aplicación (la que da Hilt), con el único tarro de cookies de la sesión.
     * [createAuthenticatedApi] la devuelve en lugar de montar otro cliente: con dos tarros,
     * cada uno veía rotaciones de csrftoken y renovaciones de Anubis que el otro no, y los
     * POST salían con el token equivocado.
     */
    @Volatile
    internal var sharedApi: BookWyrmApi? = null

    /** Host al que apunta [sharedApi], o null mientras no se sepa. */
    @Volatile
    internal var sharedApiHostProvider: (() -> String?)? = null

    /**
     * Gson con los adaptadores que necesitan los modelos (el icono de perfil puede llegar
     * como objeto o como lista). Para leer perfiles a mano hay que usar este, no `Gson()`.
     */
    val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(
                com.ferlagod.rocinante.data.model.ProfileIcon::class.java,
                com.ferlagod.rocinante.data.model.ProfileIconDeserializer()
            )
            .setLenient()
            .create()
    }

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

    /** Host en minúsculas, sin esquema, ruta ni «www.». */
    internal fun cleanHost(h: String): String =
        h.lowercase().trim().removePrefix("http://").removePrefix("https://").substringBefore("/").removePrefix("www.")

    /**
     * ¿Es [requestHost] la instancia [instanceHost] o un subdominio suyo? Sin instancia
     * conocida la respuesta es no: ante la duda no se mandan cookies ni cabeceras de sesión.
     */
    fun isInstanceHost(requestHost: String, instanceHost: String?): Boolean {
        val h = cleanHost(instanceHost.orEmpty())
        if (h.isEmpty()) return false
        val r = cleanHost(requestHost)
        return r == h || r.endsWith(".$h")
    }

    /**
     * ¿Lleva esta dirección a la página de login? Es lo que hace Django con una sesión que ya
     * no vale: en lugar de un 401, un 302 a `/login?next=...`.
     */
    fun isLoginLocation(location: String?, requestUrl: HttpUrl? = null): Boolean {
        if (location.isNullOrBlank()) return false
        val path = requestUrl?.resolve(location)?.encodedPath
            ?: location.toHttpUrlOrNull()?.encodedPath
            ?: location.substringBefore('?')
        return path.startsWith("/login")
    }

    /** ¿Es la respuesta una redirección a la página de login? */
    fun isLoginRedirect(response: okhttp3.Response): Boolean =
        response.isRedirect && isLoginLocation(response.header("Location"), response.request.url)

    /**
     * Pone el token CSRF en el campo `csrfmiddlewaretoken` de un formulario: lo añade si falta
     * y lo rellena si viene vacío. Un token ya relleno se respeta. Si el cuerpo no es un
     * formulario devuelve null y la petición sigue tal cual.
     */
    internal fun formWithCsrf(body: RequestBody?, csrf: String): FormBody? {
        if (body !is FormBody) return null
        val newFormBody = FormBody.Builder()
        var hasCsrf = false
        for (i in 0 until body.size) {
            val name = body.name(i)
            val value = body.value(i)
            if (name == "csrfmiddlewaretoken") {
                newFormBody.add(name, if (value.isBlank()) csrf else value)
                hasCsrf = true
            } else {
                newFormBody.add(name, value)
            }
        }
        if (!hasCsrf) {
            newFormBody.add("csrfmiddlewaretoken", csrf)
        }
        return newFormBody.build()
    }

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

    /** Limpia las cookies en memoria del cliente activo. */
    fun clearCookies() {
        (lastOkHttpClient?.cookieJar as? SessionCookieJar)?.clear()
    }

    /** Vacía la caché HTTP en disco del cliente activo (al cerrar sesión o cambiar de cuenta). */
    fun evictHttpCache() {
        try {
            lastOkHttpClient?.cache?.evictAll()
        } catch (_: Exception) {}
    }

    @Volatile
    private var imageClientBase: OkHttpClient? = null

    @Volatile
    private var imageClient: OkHttpClient? = null

    private val fallbackImageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Cliente para las imágenes. Parte del cliente de sesión (mismo UA y, para la instancia,
     * mismas cookies, que hacen falta tras Anubis) pero sí sigue redirecciones: las portadas
     * de OpenLibrary y las de instancias con almacenamiento externo redirigen con 302.
     * Se resuelve en cada petición, así no importa si Coil arranca antes que la sesión.
     */
    private fun currentImageClient(): OkHttpClient {
        val base = lastOkHttpClient ?: return fallbackImageClient
        val cached = imageClient
        if (cached != null && imageClientBase === base) return cached
        val built = base.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        imageClientBase = base
        imageClient = built
        return built
    }

    /** Cabeceras que solo tienen sentido hacia la instancia del usuario. */
    private val SESSION_HEADERS = listOf("X-CSRFToken", "Origin", "Referer")

    /**
     * La petición con la que seguir una redirección 307/308, o null si no hay que seguirla.
     *
     * Dentro de la instancia se repite la petición tal cual. Hacia otro servidor (una portada
     * guardada en un almacenamiento externo, por ejemplo) se quitan las cabeceras de la sesión,
     * que no son de su incumbencia: antes viajaban también el token CSRF y el origen. Y una
     * petición con cuerpo, como un POST, no se sigue fuera de la instancia, porque el cuerpo
     * lleva el token en el formulario; se devuelve la redirección a quien la pidió.
     */
    internal fun redirectRequest(request: Request, newUrl: HttpUrl, instanceHost: String?): Request? {
        val builder = request.newBuilder().url(newUrl)
        if (isInstanceHost(newUrl.host, instanceHost)) return builder.build()
        if (request.body != null) return null
        SESSION_HEADERS.forEach { builder.removeHeader(it) }
        return builder.build()
    }

    /** Fábrica de llamadas para Coil. */
    val imageCallFactory: Call.Factory = Call.Factory { request -> currentImageClient().newCall(request) }

    fun createAuthenticatedApi(
        baseUrl: String,
        cookieString: String,
        onCookiesUpdated: ((String) -> Unit)? = null
    ): BookWyrmApi {
        val cleanUrl = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
        val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        val host = finalUrl.toHttpUrlOrNull()?.host?.lowercase() ?: cleanUrl.lowercase()

        // Si ya existe la API de la aplicación y apunta a esta misma instancia, se usa esa:
        // un solo tarro de cookies para toda la app.
        val shared = sharedApi
        val sharedHost = sharedApiHostProvider?.invoke()
        if (shared != null && sharedHost != null && cleanHost(sharedHost) == cleanHost(host)) {
            return shared
        }

        val scheme = finalUrl.toHttpUrlOrNull()?.scheme ?: "https"
        val originHeader = "$scheme://$host"
        val refererHeader = "$originHeader/"

        // CookieJar con las cookies de sesión iniciales
        val cookieJar = SessionCookieJar(cookieString, host, onCookiesUpdated)

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val isToInstance = isInstanceHost(request.url.host, host)
            val requestBuilder = request.newBuilder()
                .header("User-Agent", userAgent)

            if (isToInstance) {
                requestBuilder.header("Origin", originHeader).header("Referer", refererHeader)
            }

            val acceptHeader = request.header("Accept")
            if (acceptHeader?.contains("text/html") != true) {
                requestBuilder.header("X-Requested-With", "XMLHttpRequest")
            }

            if (acceptHeader == null) {
                requestBuilder.header("Accept", "application/activity+json, application/json")
            }

            // Leer el CSRF más reciente del jar (puede haber rotado tras un POST previo)
            val csrfToken = if (isToInstance) cookieJar.currentCsrfToken() else null
            if (csrfToken != null) {
                requestBuilder.header("X-CSRFToken", csrfToken)
                formWithCsrf(request.body, csrfToken)?.let { requestBuilder.method(request.method, it) }
            }

            var response = chain.proceed(requestBuilder.build())

            // Anubis: cuando su cookie de paso caduca, la instancia responde con 307 hacia el reto
            // o 403. Se resuelve el reto y se repite la petición. Solo para la propia instancia:
            // el reto de otro servidor no se arregla renovando la cookie de esta.
            if (isToInstance && AnubisClearance.isChallenge(response)) {
                val fresh = AnubisClearance.refreshBlocking(finalUrl, cookieJar.asCookieString())
                if (fresh != null) {
                    cookieJar.merge(fresh)
                    response.close()
                    val newCsrf = cookieJar.currentCsrfToken()
                    if (newCsrf != null) {
                        requestBuilder.header("X-CSRFToken", newCsrf)
                        formWithCsrf(request.body, newCsrf)?.let { requestBuilder.method(request.method, it) }
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
                val newRequest = redirectRequest(response.request, newUrl, host) ?: break
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
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

        // Este cliente suelto solo se queda como «el de la app» si aún no hay otro: el Worker
        // de recordatorios lo crea en segundo plano y no debe quitarle el tarro a la interfaz.
        if (lastOkHttpClient == null) {
            lastOkHttpClient = okHttpClient
            lastInstanceHost = host
        }

        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookWyrmApi::class.java)
    }
}
