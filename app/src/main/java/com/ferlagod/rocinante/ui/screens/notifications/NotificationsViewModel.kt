package com.ferlagod.rocinante.ui.screens.notifications
import com.ferlagod.rocinante.data.model.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.local.SessionStorage
import com.ferlagod.rocinante.data.model.NotificationUiItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ferlagod.rocinante.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Representa el estado actual de la pantalla de Notificaciones.
 */
sealed class NotificationsState {
    object Loading : NotificationsState()
    data class Success(val notifications: List<NotificationUiItem>) : NotificationsState()
    data class Error(val message: String) : NotificationsState()
}




/**
 * ViewModel encargado de gestionar la lógica de presentación de la pestaña de notificaciones.
 * Extrae y mantiene el estado de las notificaciones desde la web de la instancia, proporcionando
 * opciones para refrescar y actualizar el contenido.
 *
 * @param api Cliente autenticado de BookWyrm.
 * @param sessionStorage Almacenamiento local para obtener la URL de la instancia.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: BookWyrmApi,
    private val sessionStorage: SessionStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val instanceUrl: String
        get() = sessionStorage.currentSession?.instanceUrl ?: ""

    private val _state = MutableStateFlow<NotificationsState>(NotificationsState.Loading)
    val state: StateFlow<NotificationsState> = _state.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadNotifications()
    }

    /**
     * Carga inicial o recarga forzada de las notificaciones.
     * Cambia el estado a [NotificationsState.Loading] antes de recuperar los datos.
     */
    fun loadNotifications() {
        viewModelScope.launch {
            _state.value = NotificationsState.Loading
            try {
                val items = BookWyrmScraper.scrapeNotifications(api, instanceUrl)
                _state.value = NotificationsState.Success(items)
            } catch (e: Exception) {
                _state.value = NotificationsState.Error(e.message ?: context.getString(R.string.error_unknown))
            }
        }
    }

    /**
     * Refresca las notificaciones de manera silenciosa mediante Pull-to-refresh.
     * Mantiene el estado anterior visible mientras carga los nuevos datos en segundo plano.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val items = BookWyrmScraper.scrapeNotifications(api, instanceUrl)
                _state.value = NotificationsState.Success(items)
            } catch (e: Exception) {
                // Ignore error on refresh or show snackbar
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Limpia todas las notificaciones leídas enviando una petición al servidor.
     * Si la petición tiene éxito, se actualiza el estado a una lista vacía.
     */
    fun clearAllNotifications(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val success = BookWyrmScraper.clearNotifications(api, instanceUrl)
                if (success) {
                    _state.value = NotificationsState.Success(emptyList())
                } else {
                    val items = BookWyrmScraper.scrapeNotifications(api, instanceUrl)
                    _state.value = NotificationsState.Success(items)
                }
                onComplete()
            } catch (e: Exception) {
                onComplete()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

