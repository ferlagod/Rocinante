/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 Fernando Lago (ferlagod)
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU Affero General Public License (AGPLv3).
 * * AVISO DE DOBLE LICENCIA: Para uso comercial o propietario sin 
 * las obligaciones de liberación de código de la AGPLv3, 
 * es necesario adquirir una licencia comercial previa.
 */
package com.ferlagod.rocinante.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.local.SessionStorage
import com.ferlagod.rocinante.data.model.SessionData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Representa el estado de sesión actual en la interfaz de usuario.
 *
 * @property isCheckingSession Indica si se está verificando asíncronamente el estado de sesión localmente.
 * @property session Objeto con los datos de sesión, o null si no hay ninguna sesión activa.
 */
data class SessionUiState(
    val isCheckingSession: Boolean = true,
    val session: SessionData? = null
)

/**
 * ViewModel encargado de gestionar el estado global de la sesión del usuario en la aplicación.
 * Observa el almacenamiento local y expone los cambios reactivamente a la UI.
 *
 * @property sessionStorage Componente responsable de la persistencia segura de los datos de sesión.
 */
class SessionViewModel(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    
    /**
     * Flujo de estado inmutable para observar los cambios en la sesión desde la vista.
     */
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStorage.sessionFlow.collect { session ->
                _uiState.value = SessionUiState(
                    isCheckingSession = false,
                    session = session
                )
            }
        }
    }

    /**
     * Persiste los datos de inicio de sesión de un usuario y actualiza el estado.
     *
     * @param session Instancia con las credenciales y datos de acceso.
     */
    fun saveSession(session: SessionData) {
        viewModelScope.launch {
            sessionStorage.saveSession(session)
        }
    }

    /**
     * Finaliza la sesión activa eliminando los datos de almacenamiento local.
     */
    fun logout() {
        viewModelScope.launch {
            sessionStorage.clearSession()
        }
    }
}

/**
 * Fábrica para instanciar [SessionViewModel] con sus dependencias.
 *
 * @property sessionStorage Almacenamiento persistente inyectado en el ViewModel.
 */
class SessionViewModelFactory(
    private val sessionStorage: SessionStorage
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(sessionStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}