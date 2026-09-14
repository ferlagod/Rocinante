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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ferlagod.rocinante.data.local.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response

/**
 * Renueva la autorización de Anubis, el filtro anti-bots que algunas instancias
 * (entre ellas bookwyrm.social) ponen delante de BookWyrm.
 *
 * Anubis solo desafía a lo que parece un navegador, y Rocinante envía a propósito el
 * User-Agent del WebView (ver [NetworkClient.userAgent]), así que siempre es desafiado.
 * Al iniciar sesión, el WebView resuelve el reto y la instancia emite la cookie
 * `techaro.lol-anubis-auth`, que se guarda junto a las de sesión.
 *
 * El problema: esa cookie caduca a los pocos días, mientras que `sessionid` dura un año.
 * Cuando eso ocurre la instancia responde a cada petición con un 307 hacia su reto, y el
 * cuerpo que llega es la página HTML "Making sure you're not a bot!". Retrofit intenta
 * leerla como JSON y falla con un desconcertante «Expected BEGIN_OBJECT but was STRING at
 * line 1 column 1 path $», que en la interfaz aparece como un error de la pantalla en la
 * que estuviera el usuario. La sesión sigue siendo válida: lo único caducado es el permiso
 * de paso, y hasta ahora la única forma de recuperarlo era cerrar sesión y volver a entrar.
 *
 * Aquí se resuelve el reto otra vez en un WebView invisible —es JavaScript, así que lo
 * ejecuta el propio motor, sin reimplementar el protocolo de Anubis— y la cookie recién
 * emitida se propaga a los dos clientes HTTP y a la sesión guardada.
 */
object AnubisClearance {

    /** Todo lo que Anubis sirve cuelga de esta ruta. */
    const val CHALLENGE_PATH = "/.within.website/"

    /** Cookie de paso que emite Anubis al superar el reto. */
    const val COOKIE_NAME = "techaro.lol-anubis-auth"

    /** Margen para resolver la prueba de trabajo; en un móvil suele bastar con unos segundos. */
    private const val TIMEOUT_MS = 30_000L

    /** Cada cuánto se mira si el WebView ya ha conseguido la cookie. */
    private const val POLL_MS = 250L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sessionStorage: SessionStorage? = null

    /** Serializa las renovaciones: varias peticiones fallando a la vez solo provocan una. */
    private val mutex = Mutex()

    /**
     * Guarda lo que hace falta para poder renovar desde el interceptor, que no tiene
     * acceso ni al contexto ni a la sesión.
     */
    fun init(context: Context, storage: SessionStorage) {
        appContext = context.applicationContext
        sessionStorage = storage
    }

    /**
     * ¿Este intercambio es el reto de Anubis en lugar de lo que se había pedido?
     *
     * Cubre todos los casos: la redirección hacia el reto (307), haber acabado dentro de él
     * porque el cliente siguió la redirección, o un error 403 originado por Anubis.
     */
    /**
     * ¿Este intercambio es el reto de Anubis en lugar de lo que se había pedido?
     *
     * Cubre todos los casos: la redirección hacia el reto (307/308), haber acabado dentro de él
     * porque el cliente siguió la redirección, un 200 con Set-Cookie del reto, o un error 403 originado por Anubis.
     */
    fun isChallenge(response: Response): Boolean =
        isChallengeRedirect(response) || 
        response.request.url.encodedPath.startsWith(CHALLENGE_PATH) ||
        isAnubisChallengeResponse(response)

    /** ¿Es una redirección que lleva al reto de Anubis? */
    fun isChallengeRedirect(response: Response): Boolean =
        response.isRedirect && response.header("Location")?.contains(CHALLENGE_PATH) == true

    /** ¿Indica la respuesta que es un reto activo de Anubis? */
    fun isAnubisChallengeResponse(response: Response): Boolean {
        val server = response.header("Server") ?: ""
        val location = response.header("Location") ?: ""
        val setCookie = response.headers("Set-Cookie").joinToString("; ")
        val isAnubis = server.contains("anubis", ignoreCase = true) || 
               location.contains(CHALLENGE_PATH) ||
               setCookie.contains("techaro.lol-anubis")

        if (response.code == 403 && isAnubis) return true

        // Reto servido con HTTP 200: Anubis borra la cookie de auth o envía verificación
        if (response.code == 200 && (setCookie.contains("techaro.lol-anubis-cookie-verification") || setCookie.contains("techaro.lol-anubis-auth=;"))) {
            return true
        }

        if (response.code == 200 && isAnubis && response.body?.contentType()?.subtype?.contains("html") == true && response.request.header("Accept")?.contains("json") == true) {
            return true
        }

        return false
    }

    /** ¿Indican las cabeceras de la respuesta que proviene de Anubis? */
    fun isAnubisResponse(response: Response): Boolean {
        val server = response.header("Server") ?: ""
        val location = response.header("Location") ?: ""
        val setCookie = response.headers("Set-Cookie").joinToString("; ")
        return server.contains("anubis", ignoreCase = true) || 
               location.contains(CHALLENGE_PATH) ||
               setCookie.contains("techaro.lol-anubis")
    }

    /**
     * Versión bloqueante para usar desde un interceptor de OkHttp, que no es suspend.
     * Nunca se llama desde el hilo principal: los interceptores corren en los hilos de OkHttp.
     *
     * @param instanceUrl Instancia cuya autorización hay que renovar.
     * @param staleCookie Cookies con las que falló la petición, para saber si otra
     *        renovación simultánea ya ha conseguido una nueva.
     * @return La cadena de cookies actualizada, o null si no se pudo renovar.
     */
    fun refreshBlocking(instanceUrl: String?, staleCookie: String?): String? =
        runBlocking { refresh(instanceUrl, staleCookie) }

    /**
     * Resuelve el reto y propaga la cookie resultante.
     *
     * @return La cadena de cookies actualizada, o null si no se pudo renovar.
     */
    suspend fun refresh(instanceUrl: String?, staleCookie: String?): String? = mutex.withLock {
        val base = baseUrlOf(instanceUrl) ?: return null
        val staleToken = tokenOf(staleCookie)

        // Mientras esperábamos el turno, otra petición puede haber renovado ya.
        val current = cookiesFor(base)
        if (current != null && tokenOf(current) != null && tokenOf(current) != staleToken) {
            return propagate(current)
        }

        val solved = solveChallenge(base, staleToken, staleCookie) ?: return null
        propagate(solved)
    }

    /**
     * Carga la instancia en un WebView invisible para que su JavaScript resuelva el reto,
     * y espera a que aparezca una cookie de paso distinta de la caducada.
     *
     * El WebView comparte el almacén de cookies con el del login, así que llega con la
     * sesión ya iniciada; solo hay que igualar el User-Agent, porque es a ese UA al que
     * Anubis emite la cookie.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun solveChallenge(base: String, staleToken: String?, staleCookie: String?): String? {
        val context = appContext ?: return null
        val cookieManager = CookieManager.getInstance()
        var webView: WebView? = null
        try {
            withContext(Dispatchers.Main) {
                cookieManager.setAcceptCookie(true)
                if (staleCookie != null) {
                    staleCookie.split(";").map { it.trim() }.forEach {
                        cookieManager.setCookie(base, it)
                    }
                    cookieManager.flush()
                }
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = NetworkClient.userAgent
                    webViewClient = WebViewClient()
                    loadUrl(base)
                }
            }
            return withTimeoutOrNull(TIMEOUT_MS) {
                while (true) {
                    delay(POLL_MS)
                    val cookies = cookiesFor(base)
                    val token = tokenOf(cookies)
                    if (token != null && token != staleToken) {
                        withContext(Dispatchers.Main) { cookieManager.flush() }
                        return@withTimeoutOrNull cookies
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    /**
     * Reparte la cookie recién emitida: al tarro del cliente con CookieJar, y a la sesión
     * guardada, de la que lee el cliente inyectado y que además sobrevive al reinicio.
     */
    private suspend fun propagate(cookies: String): String {
        NetworkClient.replaceCookies(cookies)
        sessionStorage?.let { storage ->
            storage.currentSession?.let { sess ->
                if (sess.cookie != cookies) {
                    try {
                        storage.saveSession(sess.copy(cookie = cookies))
                    } catch (_: Exception) {}
                }
            }
        }
        return cookies
    }

    /** Cookies que el WebView tiene ahora mismo para esa instancia. */
    private suspend fun cookiesFor(base: String): String? =
        withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(base) }

    /** Valor de la cookie de paso dentro de una cadena "nombre=valor; nombre=valor". */
    internal fun tokenOf(cookies: String?): String? {
        if (cookies.isNullOrBlank()) return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE_NAME=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    /** Normaliza la instancia a "https://host/", que es la clave del almacén de cookies. */
    internal fun baseUrlOf(instanceUrl: String?): String? {
        if (instanceUrl.isNullOrBlank()) return null
        val withScheme = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
