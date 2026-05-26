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

data class SessionUiState(
    val isCheckingSession: Boolean = true,
    val session: SessionData? = null
)

class SessionViewModel(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
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

    fun saveSession(session: SessionData) {
        viewModelScope.launch {
            sessionStorage.saveSession(session)
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionStorage.clearSession()
        }
    }
}

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