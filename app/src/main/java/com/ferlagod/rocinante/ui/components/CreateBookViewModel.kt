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
import com.ferlagod.rocinante.data.repository.SearchRepository
import com.ferlagod.rocinante.utils.CreateBookForm
import com.ferlagod.rocinante.utils.IsbnUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * queda lo que está en marcha: la creación, la consulta a OpenLibrary, la comprobación de
 * duplicados, las sugerencias de autores y la confirmación que pide la instancia.
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

    /** En qué va la creación, para decírselo al usuario mientras espera. */
    enum class Step { CHECKING_DUPLICATES, PREPARING_COVER, SENDING, CONFIRMING, SHELVING }

    /** Libro con el mismo ISBN que ya está en la instancia. */
    data class Duplicate(
        val isbn: String,
        val url: String,
        /** Salió al guardar: «Crear igualmente» tiene que seguir con la creación. */
        val fromSubmit: Boolean
    )

    /** Autores de la instancia que casan con lo que se escribe en el campo [index]. */
    data class AuthorSuggestions(val index: Int, val names: List<String>)

    sealed class Event {
        /** Libro creado. [shelved] es null si no se pidió estantería. */
        data class Created(val bookUrl: String, val title: String, val andAddAnother: Boolean, val shelved: Boolean?) : Event()
        data class Failed(val message: String) : Event()
        /** Resultado de rellenar por ISBN; null si OpenLibrary no tiene el libro. */
        data class LookupDone(val data: OpenLibraryLookup.BookData?) : Event()
    }

    data class UiState(
        val step: Step? = null,
        val isLookingUp: Boolean = false,
        val confirmation: Confirmation? = null,
        val duplicate: Duplicate? = null,
        val authorSuggestions: AuthorSuggestions? = null,
        val event: Event? = null
    ) {
        val isWorking: Boolean get() = step != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sessionKey: String? = null
    private var pendingRequest: Request? = null
    private var job: Job? = null
    private var lookupJob: Job? = null
    private var duplicateJob: Job? = null
    private var suggestionsJob: Job? = null

    /** Último ISBN consultado en la instancia y lo que salió (null: no lo tiene). */
    private var checkedIsbn: String? = null
    private var checkedUrl: String? = null
    /** ISBN repetido con el que el usuario ha dicho que se cree igualmente. */
    private var acceptedDuplicateIsbn: String? = null
    /** Autores ya buscados, para no repetir la consulta al borrar y volver a escribir. */
    private val authorCache = mutableMapOf<String, List<String>>()

    /**
     * Cada vez que se abre el diálogo trae una clave nueva (guardada con rememberSaveable, así
     * que al girar es la misma): si cambia, lo de la vez anterior no vale.
     */
    fun start(key: String) {
        if (sessionKey == key) return
        sessionKey = key
        listOf(job, lookupJob, duplicateJob, suggestionsJob).forEach { it?.cancel() }
        pendingRequest = null
        checkedIsbn = null
        checkedUrl = null
        acceptedDuplicateIsbn = null
        _state.value = UiState()
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    fun cancelConfirmation() = _state.update { it.copy(confirmation = null) }

    fun select(option: BookWyrmScraper.AuthorOption) = _state.update { state ->
        val confirmation = state.confirmation ?: return@update state
        state.copy(confirmation = confirmation.copy(selections = confirmation.selections + (option.field to option)))
    }

    /** Pide a OpenLibrary los datos del ISBN. */
    fun lookupIsbn(isbn: String) {
        if (_state.value.isLookingUp || _state.value.isWorking) return
        _state.update { it.copy(isLookingUp = true) }
        lookupJob = viewModelScope.launch {
            val data = OpenLibraryLookup.lookup(isbn)
            _state.update { it.copy(isLookingUp = false, event = Event.LookupDone(data)) }
        }
    }

    /**
     * Mira en la instancia si ya tiene el ISBN, sin esperar a que se pulse Guardar. Se llama
     * cada vez que cambia el campo; espera un poco por si se sigue tecleando.
     */
    fun checkDuplicate(api: BookWyrmApi, isbn: String) {
        val clean = IsbnUtils.normalize(isbn).takeIf { IsbnUtils.isIsbn(it) }
        duplicateJob?.cancel()
        _state.update { state -> if (state.duplicate?.isbn != clean) state.copy(duplicate = null) else state }
        if (clean == null || clean == checkedIsbn) {
            // Al girar se vuelve a llamar con el mismo ISBN: el aviso que ya está se deja igual.
            if (clean != null && _state.value.duplicate?.isbn != clean) showDuplicateIfAny(clean, fromSubmit = false)
            return
        }
        duplicateJob = viewModelScope.launch {
            delay(DUPLICATE_DEBOUNCE_MS)
            val existing = BookWyrmScraper.findBookUrlByIsbn(api, clean)
            checkedIsbn = clean
            checkedUrl = existing
            showDuplicateIfAny(clean, fromSubmit = false)
        }
    }

    private fun showDuplicateIfAny(isbn: String, fromSubmit: Boolean): Boolean {
        val url = checkedUrl.takeIf { checkedIsbn == isbn } ?: return false
        if (isbn == acceptedDuplicateIsbn) return false
        _state.update { it.copy(duplicate = Duplicate(isbn, url, fromSubmit)) }
        return true
    }

    /** El usuario sabe que está repetido y quiere crearlo igualmente. */
    fun acceptDuplicate() {
        val duplicate = _state.value.duplicate ?: return
        acceptedDuplicateIsbn = duplicate.isbn
        _state.update { it.copy(duplicate = null) }
    }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }

    /**
     * Busca en la instancia autores que casen con lo escrito en el campo [index]. Espera un
     * poco por si se sigue tecleando y solo pregunta a la base de datos de la instancia.
     */
    fun suggestAuthors(api: BookWyrmApi, instanceUrl: String, index: Int, query: String) {
        suggestionsJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_AUTHOR_QUERY) {
            _state.update { it.copy(authorSuggestions = null) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            val key = trimmed.lowercase()
            val names = authorCache[key] ?: run {
                delay(SUGGESTIONS_DEBOUNCE_MS)
                SearchRepository(api).searchBooksScraped(trimmed, instanceUrl)
                    .map { it.author }
                    .let { CreateBookForm.matchingAuthors(trimmed, it) }
                    .also { authorCache[key] = it }
            }
            _state.update { it.copy(authorSuggestions = AuthorSuggestions(index, names).takeIf { names.isNotEmpty() }) }
        }
    }

    fun clearAuthorSuggestions() {
        suggestionsJob?.cancel()
        _state.update { it.copy(authorSuggestions = null) }
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
        suggestionsJob?.cancel()
        _state.update { it.copy(step = Step.CHECKING_DUPLICATES, duplicate = null, authorSuggestions = null) }
        job = viewModelScope.launch {
            val clean = IsbnUtils.normalize(request.isbn)
            if (checkDuplicates && IsbnUtils.isIsbn(clean) && clean != acceptedDuplicateIsbn) {
                // Si ya se miró este ISBN mientras se escribía, no hace falta volver a preguntar.
                if (clean != checkedIsbn) {
                    duplicateJob?.cancel()
                    checkedUrl = BookWyrmScraper.findBookUrlByIsbn(api, clean)
                    checkedIsbn = clean
                }
                if (showDuplicateIfAny(clean, fromSubmit = true)) {
                    _state.update { it.copy(step = null) }
                    return@launch
                }
            }
            _state.update { it.copy(step = Step.PREPARING_COVER) }
            val (coverBytes, coverMime) = cover()
            _state.update { it.copy(step = Step.SENDING) }
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
        _state.update { it.copy(step = Step.CONFIRMING) }
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
                val slug = request?.shelfSlug?.takeIf { it.isNotBlank() && result.bookId.isNotBlank() }
                val shelved = slug?.let {
                    _state.update { state -> state.copy(step = Step.SHELVING) }
                    BookWyrmScraper.shelveCreatedBook(api, result.bookId, it)
                }
                // El libro ya existe: si se da de alta otro con el mismo ISBN, que avise.
                IsbnUtils.normalize(request?.isbn.orEmpty()).takeIf { IsbnUtils.isIsbn(it) }?.let {
                    checkedIsbn = it
                    checkedUrl = result.bookUrl
                }
                _state.update {
                    it.copy(
                        step = null,
                        confirmation = null,
                        event = Event.Created(result.bookUrl, request?.title.orEmpty(), request?.andAddAnother == true, shelved)
                    )
                }
            }
            is BookWyrmScraper.CreateBookResult.NeedsAuthorConfirmation -> {
                // Por defecto, la primera opción de cada grupo (ya vienen ordenadas).
                val defaults = result.options.groupBy { it.field }.mapValues { (_, group) -> group.first() }
                _state.update {
                    it.copy(step = null, confirmation = Confirmation(result.form, result.options, defaults))
                }
            }
            is BookWyrmScraper.CreateBookResult.Error -> {
                _state.update { it.copy(step = null, event = Event.Failed(result.message)) }
            }
        }
    }

    private companion object {
        const val DUPLICATE_DEBOUNCE_MS = 500L
        const val SUGGESTIONS_DEBOUNCE_MS = 400L
        const val MIN_AUTHOR_QUERY = 3
    }
}
