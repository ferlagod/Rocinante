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
package com.ferlagod.rocinante.ui.components

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.BookWyrmAuthor
import com.ferlagod.rocinante.utils.BookWyrmUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Lo que la ficha de un libro descarga al abrirse: los datos de su página (valoración, fechas,
 * estantería), el progreso de lectura y las fichas de sus autores.
 *
 * Antes se pedía desde la propia ficha, y cualquier cosa que la rehiciera (girar el móvil, por
 * ejemplo) volvía a pedirlo todo. Aquí se pide una vez por apertura y los autores van en
 * paralelo en vez de uno detrás de otro. Las acciones de la ficha siguen en ella.
 */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    data class State(
        val enrichment: BookEnrichment? = null,
        /** Enriquecimiento recién leído de la web, para avisar una vez a quien abrió la ficha. */
        val freshEnrichment: BookEnrichment? = null,
        val authors: List<BookWyrmAuthor> = emptyList(),
        val readingProgress: BookWyrmScraper.ReadingProgressInfo? = null,
        val isLoadingProgress: Boolean = false
    )

    private val cache = TimelineCache(context)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var sessionKey: String? = null
    private var progressKey: Triple<String, String?, Int>? = null
    private var progressJob: Job? = null
    private var authorUrlsLoaded: List<String> = emptyList()
    private var authorsJob: Job? = null

    /**
     * Empieza a cargar la ficha. [session] cambia en cada apertura (y se conserva al girar):
     * con la misma, no se vuelve a pedir nada.
     */
    fun bind(
        session: String,
        api: BookWyrmApi,
        bookKey: String,
        initialEnrichment: BookEnrichment?,
        authorUrls: List<String>
    ) {
        if (sessionKey == session) return
        sessionKey = session
        progressKey = null
        progressJob?.cancel()
        authorsJob?.cancel()
        authorUrlsLoaded = emptyList()
        _state.value = State(enrichment = initialEnrichment)

        viewModelScope.launch {
            val fresh = runCatching { BookWyrmScraper.scrapeBookEnrichment(api, bookKey) }.getOrNull()
            if (fresh != null && sessionKey == session) {
                // Se cachea desde aquí, y no solo avisando a quien abrió la ficha: por la
                // búsqueda y la actividad pasan los libros que aún no están en ninguna
                // estantería, que son justo los que se acaban de añadir. Guardado ya, «Mis
                // libros» los enseña completos sin volver a leer su página.
                runCatching { cache.mergeEnrichment(fresh) }
                _state.update { it.copy(enrichment = fresh, freshEnrichment = fresh) }
            }
        }

        updateAuthors(api, authorUrls)
    }

    /**
     * Los autores, si la lista ha cambiado: quien abre la ficha puede enseñarla primero con lo
     * guardado y luego cambiarla por lo recién descargado.
     */
    fun updateAuthors(api: BookWyrmApi, authorUrls: List<String>) {
        val session = sessionKey ?: return
        val urls = authorUrls.filter { it.isNotBlank() }.distinct()
        if (urls == authorUrlsLoaded) return
        authorUrlsLoaded = urls
        authorsJob?.cancel()
        if (urls.isEmpty()) {
            _state.update { it.copy(authors = emptyList()) }
            return
        }
        loadAuthors(session, api, urls)
    }

    /**
     * El .json del libro solo trae las direcciones de sus autores; el nombre y lo demás están
     * en la ficha de cada uno. Varias a la vez (pocas, para no cargar al servidor) y en el orden
     * del libro; las ya guardadas no cuestan nada.
     */
    private fun loadAuthors(session: String, api: BookWyrmApi, urls: List<String>) {
        authorsJob = viewModelScope.launch {
            val limiter = Semaphore(3)
            val loaded = arrayOfNulls<BookWyrmAuthor>(urls.size)
            urls.mapIndexed { index, url ->
                async {
                    val author = cache.loadAuthor(url) ?: limiter.withPermit {
                        runCatching { api.getAuthor(BookWyrmUtils.ensureJsonUrl(url)) }.getOrNull()
                    }?.also { runCatching { cache.saveAuthor(url, it) } }
                    if (author != null && sessionKey == session) {
                        loaded[index] = author
                        // Se van enseñando según llegan, en vez de esperar a tenerlos todos.
                        _state.update { it.copy(authors = loaded.filterNotNull()) }
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Progreso de lectura, solo si el libro está en «Leyendo». [refresh] sube cuando la ficha
     * quiere volver a pedirlo; con el mismo libro, estantería y [refresh] no se repite.
     */
    fun loadProgress(api: BookWyrmApi, bookKey: String, shelf: String?, refresh: Int) {
        val key = Triple(bookKey, shelf, refresh)
        if (progressKey == key) return
        progressKey = key
        progressJob?.cancel()
        if (shelf != "reading") {
            _state.update { it.copy(readingProgress = null, isLoadingProgress = false) }
            return
        }
        _state.update { it.copy(isLoadingProgress = true) }
        progressJob = viewModelScope.launch {
            val progress = runCatching { BookWyrmScraper.getReadingProgress(api, bookKey) }.getOrNull()
            _state.update { it.copy(readingProgress = progress, isLoadingProgress = false) }
        }
    }

    /** El progreso que se acaba de anotar desde la ficha. */
    fun setProgress(progress: BookWyrmScraper.ReadingProgressInfo) =
        _state.update { it.copy(readingProgress = progress) }

    /** Datos de la página releídos por la ficha (p. ej. tras cambiar las fechas de lectura). */
    fun setEnrichment(enrichment: BookEnrichment) =
        _state.update { it.copy(enrichment = enrichment) }

    fun consumeFreshEnrichment() = _state.update { it.copy(freshEnrichment = null) }
}
