package com.ferlagod.rocinante.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.model.NotificationUiItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * @param instanceUrl URL base de la instancia conectada.
 */
class NotificationsViewModel(
    private val api: BookWyrmApi,
    private val instanceUrl: String
) : ViewModel() {

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
                val items = NetworkClient.scrapeNotifications(api, instanceUrl)
                _state.value = NotificationsState.Success(items)
            } catch (e: Exception) {
                _state.value = NotificationsState.Error(e.message ?: "Unknown error")
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
                val items = NetworkClient.scrapeNotifications(api, instanceUrl)
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
                val success = NetworkClient.clearNotifications(api, instanceUrl)
                if (success) {
                    _state.value = NotificationsState.Success(emptyList())
                } else {
                    val items = NetworkClient.scrapeNotifications(api, instanceUrl)
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

/**
 * Factory para instanciar [NotificationsViewModel] inyectando las dependencias necesarias.
 */
class NotificationsViewModelFactory(
    private val api: BookWyrmApi,
    private val instanceUrl: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationsViewModel(api, instanceUrl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
