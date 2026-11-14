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
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.utils.BookWyrmUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * La privacidad con la que el usuario publica por defecto, tal y como la tiene puesta en
 * BookWyrm («Privacidad de publicación por defecto» en sus preferencias).
 *
 * Es la que la web propone en todos los formularios de publicar, también en las notas que se
 * generan al cambiar un libro de estantería. Antes la app mandaba siempre «public», así que
 * quien publica solo para sus seguidores lo hacía en abierto sin saberlo.
 *
 * Se lee de la página de preferencias una vez y se guarda un rato: cambia muy de tarde en tarde,
 * y se pide antes de cada publicación.
 */
object PostPrivacy {

    /** Los valores que admite BookWyrm. */
    val LEVELS = setOf("public", "unlisted", "followers", "direct")

    /** Cuánto vale lo leído: si se cambia en la web, la app lo nota en este tiempo. */
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    private val lock = Mutex()
    private var cached: String? = null
    private var cachedAt = 0L

    /**
     * @return la privacidad por defecto del usuario, o null si no se pudo leer (sin red, sesión
     *   caducada, o una instancia que no la enseñe). Quien llama decide qué hacer sin ella; nunca
     *   se supone «public».
     */
    suspend fun defaultFor(api: BookWyrmApi): String? = lock.withLock {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < MAX_AGE_MS }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) {
            try {
                val response = api.getRawHtmlResponse("preferences/profile/")
                if (!response.isSuccessful) {
                    BookWyrmUtils.closeQuietly(response)
                    null
                } else {
                    parse(response.body()?.string().orEmpty())
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
        if (fresh != null) {
            cached = fresh
            cachedAt = now
        }
        fresh ?: cached
    }

    /**
     * La privacidad para algo que el usuario ha escrito y quiere publicar sí o sí, en una
     * pantalla sin selector: la suya por defecto, o «followers» si no se pudo leer. Nunca
     * «public» a ciegas: publicar de menos se arregla, publicar de más no.
     */
    suspend fun forPost(api: BookWyrmApi): String = defaultFor(api) ?: "followers"

    /** Olvida lo leído: al cerrar sesión o cambiar de cuenta, es de otra persona. */
    fun clear() {
        cached = null
        cachedAt = 0L
    }

    /**
     * Saca la privacidad por defecto del formulario de preferencias. Es un `<select>` de Django,
     * que marca la opción guardada con `selected`.
     */
    internal fun parse(html: String): String? {
        if (html.isBlank()) return null
        val select = org.jsoup.Jsoup.parse(html).selectFirst("select[name=default_post_privacy]")
            ?: return null
        val value = (select.selectFirst("option[selected]") ?: select.selectFirst("option"))
            ?.attr("value")?.trim()
        return value?.takeIf { it in LEVELS }
    }
}
