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
import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Repositorio encargado de sincronizar en segundo plano la estantería "Leídos" (read)
 * y enriquecer sus libros para calcular estadísticas de lectura precisas en el perfil
 * sin bloquear la interfaz ni requerir abrir la estantería manualmente.
 */
object ReadingStatsRepository {

    private val syncMutex = Mutex()

    /**
     * Sincroniza la estantería "Leídos" y enriquece los libros pendientes en segundo plano.
     *
     * Prioriza los libros más recientes (leídos en el último año) para que las estadísticas
     * del año en curso aparezcan actualizadas casi de inmediato, y continúa en segundo
     * plano con el resto de lecturas históricas sin saturar la red ni el hilo de UI.
     *
     * @param api Instancia de [BookWyrmApi].
     * @param dataCache Caché local [TimelineCache].
     * @param instanceUrl URL base de la instancia BookWyrm.
     * @param username Nombre del usuario actual.
     * @param onProgress Callback invocado en el hilo de fondo cuando hay libros o enriquecimientos nuevos.
     */
    suspend fun syncReadStats(
        api: BookWyrmApi,
        dataCache: TimelineCache,
        instanceUrl: String,
        username: String,
        onProgress: (suspend (books: List<ShelfBookItem>, enrichment: Map<String, BookEnrichment>) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            // Ya hay una sincronización activa en segundo plano; no duplicamos peticiones.
            return@withContext
        }
        try {
            var currentBooks = dataCache.loadShelfBooks("read").orEmpty()
            var currentEnrichment = dataCache.loadEnrichment().toMap()

            // Emitir inmediatamente los datos cacheados
            if (currentBooks.isNotEmpty() || currentEnrichment.isNotEmpty()) {
                onProgress?.invoke(currentBooks, currentEnrichment)
            }

            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val cleanUser = username.removePrefix("@").substringBefore("@").trim()

            // 1. Descargar páginas de la estantería "read"
            val allFetchedBooks = mutableListOf<ShelfBookItem>()
            val existingIds = mutableSetOf<String>()
            var page = 1
            var hasMore = true
            var isFirstPage = true

            while (hasMore && page <= 100) {
                val shelfJsonUrl = "${baseUrl}user/$cleanUser/books/read.json?page=$page"
                val pageItems = try {
                    val response = api.getShelfData(shelfJsonUrl)
                    response.orderedItems ?: emptyList()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val fallbackJsonUrl = "${baseUrl}user/$cleanUser/shelf/read.json?page=$page"
                    try {
                        val response = api.getShelfData(fallbackJsonUrl)
                        response.orderedItems ?: emptyList()
                    } catch (e2: Exception) {
                        if (e2 is CancellationException) throw e2
                        val shelfHtmlUrl = "${baseUrl}user/$cleanUser/books/read?page=$page"
                        BookWyrmScraper.scrapeShelfPage(api, shelfHtmlUrl, baseUrl) ?: emptyList()
                    }
                }

                if (pageItems.isEmpty()) {
                    hasMore = false
                } else {
                    val newItems = pageItems.filter { it.id == null || it.id !in existingIds }
                    if (newItems.isEmpty()) {
                        hasMore = false
                    } else {
                        newItems.forEach { it.id?.let { id -> existingIds.add(id) } }
                        allFetchedBooks.addAll(newItems)

                        // Si es la primera página (libros del último año/más recientes),
                        // guardamos y notificamos inmediatamente para alimentar la UI sin demora.
                        if (isFirstPage) {
                            isFirstPage = false
                            currentBooks = allFetchedBooks.toList()
                            dataCache.saveShelfBooks("read", currentBooks)
                            onProgress?.invoke(currentBooks, currentEnrichment)
                        }

                        page++
                        delay(60) // Pausa respetuosa para evitar ráfagas de red
                    }
                }
            }

            if (allFetchedBooks.isNotEmpty()) {
                currentBooks = allFetchedBooks.toList()
                dataCache.saveShelfBooks("read", currentBooks)
                onProgress?.invoke(currentBooks, currentEnrichment)
            }

            // 2. Enriquecer libros pendientes priorizando los más recientes
            val enrichmentSnapshot = dataCache.loadEnrichment()
            val missingIds = currentBooks.mapNotNull { it.id }.filter { id ->
                val cached = enrichmentSnapshot[id]
                cached == null || cached.schemaVersion != BookWyrmScraper.ENRICHMENT_SCHEMA_VERSION
            }

            if (missingIds.isNotEmpty()) {
                val workingEnrichment = enrichmentSnapshot.toMutableMap()
                val updatedBooksList = currentBooks.toMutableList()
                var countSinceLastNotify = 0

                for ((index, id) in missingIds.withIndex()) {
                    val enriched = BookWyrmScraper.scrapeBookEnrichment(api, id)
                    if (enriched != null) {
                        workingEnrichment[id] = enriched
                        dataCache.mergeEnrichment(enriched)

                        // Si el enriquecimiento trajo número de páginas y el libro no lo tenía,
                        // actualizamos el elemento de la lista para mayor precisión estadística.
                        if (enriched.pages != null && enriched.pages > 0) {
                            val bookIdx = updatedBooksList.indexOfFirst { it.id == id }
                            if (bookIdx >= 0 && updatedBooksList[bookIdx].pages == null) {
                                updatedBooksList[bookIdx] = updatedBooksList[bookIdx].copy(pages = enriched.pages)
                            }
                        }

                        countSinceLastNotify++

                        // Los primeros 15 libros son los más recientes (último año).
                        // Notificamos tras cada uno para que las cifras del año actual se muestren rápido.
                        // Para los libros más antiguos, notificamos cada 3 libros para reducir recomposiciones.
                        val isRecent = index < 15
                        if (isRecent || countSinceLastNotify >= 3 || index == missingIds.lastIndex) {
                            currentBooks = updatedBooksList.toList()
                            currentEnrichment = workingEnrichment.toMap()
                            dataCache.saveShelfBooks("read", currentBooks)
                            onProgress?.invoke(currentBooks, currentEnrichment)
                            countSinceLastNotify = 0
                        }
                    }
                    delay(200) // Delay no bloqueante para fluidez absoluta de la aplicación
                }

                currentBooks = updatedBooksList.toList()
                currentEnrichment = workingEnrichment.toMap()
                dataCache.saveShelfBooks("read", currentBooks)
                onProgress?.invoke(currentBooks, currentEnrichment)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Fallos de conexión se ignoran silenciosamente en fondo; se preserva la caché
        } finally {
            syncMutex.unlock()
        }
    }
}
