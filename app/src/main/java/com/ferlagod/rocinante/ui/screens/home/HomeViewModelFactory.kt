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
package com.ferlagod.rocinante.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.repository.BookWyrmRepository

class HomeViewModelFactory(
    private val repository: BookWyrmRepository,
    private val timelineCache: TimelineCache
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository, timelineCache) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}