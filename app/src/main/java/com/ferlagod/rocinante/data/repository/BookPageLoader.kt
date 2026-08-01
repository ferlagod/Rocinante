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
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.BookWyrmBookDetails

/**
 * Abre la ficha de un libro sin hacer esperar a nadie: primero lo que se guardó la última
 * vez que se abrió (al instante, sin red) y luego lo que diga la instancia, que sustituye a
 * lo anterior y queda guardado para la próxima.
 *
 * Es lo que hacen todas las pantallas desde las que se toca un libro (estantería, actividad,
 * búsqueda), así que vive aquí una sola vez.
 */
object BookPageLoader {

    /**
     * @param cacheKey id/URL con el que se guarda la ficha; el mismo que usa la pantalla
     *   para identificar el libro.
     * @param resolveDetailsUrl Da la URL .json de la que descargar la ficha. Es una función
     *   porque a veces hay que preguntar antes a la instancia (un libro federado se resuelve
     *   a su copia local), y eso no debe retrasar lo que ya está guardado.
     * @param onDetails Recibe la ficha; `fromCache` distingue la copia guardada (llega
     *   primero, puede no llegar) de la recién descargada.
     * @param onReviews Recibe las reseñas de la comunidad, que se raspan de la página web.
     *   Si no se pueden leer, llega una lista vacía.
     * @param onFailure Recibe el fallo de red; `hadCache` dice si a pesar de todo hay ficha
     *   en pantalla, en cuyo caso no suele haber nada que avisar.
     */
    suspend fun load(
        api: BookWyrmApi,
        cache: TimelineCache,
        cacheKey: String,
        resolveDetailsUrl: suspend () -> String,
        onDetails: (details: BookWyrmBookDetails, fromCache: Boolean) -> Unit,
        onReviews: (List<ActivityPubActivity>) -> Unit = {},
        onFailure: (error: Throwable, hadCache: Boolean) -> Unit = { _, _ -> }
    ) {
        val cached = cache.loadBookDetails(cacheKey)
        if (cached != null) onDetails(cached, true)

        try {
            val detailsUrl = resolveDetailsUrl()
            val fresh = api.getBookDetails(detailsUrl)
            onDetails(fresh, false)
            cache.saveBookDetails(cacheKey, fresh)

            val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
            val reviews = try {
                BookWyrmScraper.scrapeBookReviews(api, baseBookUrl)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            onReviews(reviews)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            onFailure(e, cached != null)
        }
    }
}
