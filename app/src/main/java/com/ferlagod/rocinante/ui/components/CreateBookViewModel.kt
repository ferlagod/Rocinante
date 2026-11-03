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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.OpenLibraryLookup
import com.ferlagod.rocinante.utils.IsbnUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * El trabajo del alta manual de libros. Vive aquí y no en el diálogo para que girar el móvil
 * o cambiar de app mientras se sube no corte la creación a medias (con el diálogo se cancelaba
 * y no se sabía si el libro había llegado a crearse).
 *
 * Los campos del formulario siguen en el diálogo (guardados con rememberSaveable); aquí solo
 * queda lo que está en marcha y la confirmación que pide la instancia.
 */
@HiltViewModel
class CreateBookViewModel @Inject constructor() : ViewModel() {

    /** Lo que se manda a la instancia. */
    data class Request(
        val title: String,
        val authors: List<String>,
        val subtitle: String,
        val isbn: String,
        val pages: String,
        val publishedDate: String,
        val description: String,
        val languages: String,
        val physicalFormat: String,
        val publishers: String,
        val series: String,
        val seriesNumber: String,
        val shelfSlug: String,
        val andAddAnother: Boolean
    )

    /** La instancia pregunta antes de crear: una respuesta por grupo (autor, obra, serie). */
    data class Confirmation(
        val form: BookWyrmScraper.BookEditForm,
        val options: List<BookWyrmScraper.AuthorOption>,
        val selections: Map<String, BookWyrmScraper.AuthorOption>
    )

    sealed class Event {
        /** Libro creado. [shelved] es null si no se pidió estantería. */
        data class Created(val bookUrl: String, val title: String, val andAddAnother: Boolean, val shelved: Boolean?) : Event()
        data class Failed(val message: String) : Event()
        /** Resultado de rellenar por ISBN; null si OpenLibrary no tiene el libro. */
        data class LookupDone(val data: OpenLibraryLookup.BookData?) : Event()
    }

    data class UiState(
        val isWorking: Boolean = false,
        val isLookingUp: Boolean = false,
        val confirmation: Confirmation? = null,
        /** Libro con el mismo ISBN que ya está en la instancia, para no crear otro. */
        val duplicateUrl: String? = null,
        val event: Event? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sessionKey: String? = null
    private var pendingRequest: Request? = null
    private var job: Job? = null

    /**
     * Cada vez que se abre el diálogo trae una clave nueva (guardada con rememberSaveable, así
     * que al girar es la misma): si cambia, lo de la vez anterior no vale.
     */
    fun start(key: String) {
        if (sessionKey == key) return
        sessionKey = key
        job?.cancel()
        pendingRequest = null
        _state.value = UiState()
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    fun dismissDuplicate() = _state.update { it.copy(duplicateUrl = null) }

    fun cancelConfirmation() = _state.update { it.copy(confirmation = null) }

    fun select(option: BookWyrmScraper.AuthorOption) = _state.update { state ->
        val confirmation = state.confirmation ?: return@update state
        state.copy(confirmation = confirmation.copy(selections = confirmation.selections + (option.field to option)))
    }

    /** Pide a OpenLibrary los datos del ISBN. */
    fun lookupIsbn(isbn: String) {
        if (_state.value.isLookingUp || _state.value.isWorking) return
        _state.update { it.copy(isLookingUp = true) }
        job = viewModelScope.launch {
            val data = OpenLibraryLookup.lookup(isbn)
            _state.update { it.copy(isLookingUp = false, event = Event.LookupDone(data)) }
        }
    }

    /**
     * Crea el libro. Con [checkDuplicates] y un ISBN válido, mira antes si la instancia ya lo
     * tiene y, si es así, para y lo dice en vez de crear otro.
     *
     * @param cover Prepara la portada (comprimida) justo antes de enviarla.
     */
    fun submit(
        api: BookWyrmApi,
        instanceUrl: String,
        request: Request,
        checkDuplicates: Boolean,
        cover: suspend () -> Pair<ByteArray?, String>
    ) {
        if (_state.value.isWorking) return
        pendingRequest = request
        _state.update { it.copy(isWorking = true, duplicateUrl = null) }
        job = viewModelScope.launch {
            if (checkDuplicates && IsbnUtils.isIsbn(request.isbn)) {
                val existing = BookWyrmScraper.findBookUrlByIsbn(api, request.isbn)
                if (existing != null) {
                    _state.update { it.copy(isWorking = false, duplicateUrl = existing) }
                    return@launch
                }
            }
            val (coverBytes, coverMime) = cover()
            val result = BookWyrmScraper.createBookManually(
                api = api,
                instanceUrl = instanceUrl,
                title = request.title,
                authorNames = request.authors,
                subtitle = request.subtitle,
                isbn13 = request.isbn,
                pages = request.pages,
                publishedDate = request.publishedDate,
                description = request.description,
                languages = request.languages,
                physicalFormat = request.physicalFormat,
                publishers = request.publishers,
                series = request.series,
                seriesNumber = request.seriesNumber,
                coverBytes = coverBytes,
                coverMimeType = coverMime
            )
            handle(api, result)
        }
    }

    /** Envía las respuestas a la confirmación de la instancia. */
    fun confirm(api: BookWyrmApi, cover: suspend () -> Pair<ByteArray?, String>) {
        val confirmation = _state.value.confirmation ?: return
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true) }
        job = viewModelScope.launch {
            // Si el formulario ya contiene la portada en la caché del servidor (file_resubmit),
            // no es necesario volver a subirla por la red.
            val hasCachedCover = confirmation.form.fields.any { it.first.contains("cover-clearable_file_input_cache-key") }
            val (coverBytes, coverMime) = if (hasCachedCover) null to "image/jpeg" else cover()
            val result = BookWyrmScraper.confirmCreateBook(
                api = api,
                form = confirmation.form,
                selections = confirmation.selections.values.toList(),
                coverBytes = coverBytes,
                coverMimeType = coverMime
            )
            handle(api, result)
        }
    }

    private suspend fun handle(api: BookWyrmApi, result: BookWyrmScraper.CreateBookResult) {
        val request = pendingRequest
        when (result) {
            is BookWyrmScraper.CreateBookResult.Created -> {
                val shelved = request?.shelfSlug?.takeIf { it.isNotBlank() && result.bookId.isNotBlank() }?.let { slug ->
                    BookWyrmScraper.shelveCreatedBook(api, result.bookId, slug)
                }
                _state.update {
                    it.copy(
                        isWorking = false,
                        confirmation = null,
                        event = Event.Created(result.bookUrl, request?.title.orEmpty(), request?.andAddAnother == true, shelved)
                    )
                }
            }
            is BookWyrmScraper.CreateBookResult.NeedsAuthorConfirmation -> {
                // Por defecto, la primera opción de cada grupo (ya vienen ordenadas).
                val defaults = result.options.groupBy { it.field }.mapValues { (_, group) -> group.first() }
                _state.update {
                    it.copy(isWorking = false, confirmation = Confirmation(result.form, result.options, defaults))
                }
            }
            is BookWyrmScraper.CreateBookResult.Error -> {
                _state.update { it.copy(isWorking = false, event = Event.Failed(result.message)) }
            }
        }
    }
}
