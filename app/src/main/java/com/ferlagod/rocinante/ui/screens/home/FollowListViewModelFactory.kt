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
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.local.FollowListCache

/**
 * Fábrica para instanciar [FollowListViewModel].
 * Facilita la inyección de dependencias necesarias (cliente de red y sistema de caché).
 *
 * @property api Interfaz del cliente para llamadas a la API de BookWyrm.
 * @property cache Mecanismo de persistencia local para listas de seguimiento.
 */
class FollowListViewModelFactory(
    private val api: BookWyrmApi,
    private val cache: FollowListCache
) : ViewModelProvider.Factory {
    /**
     * Instancia y retorna un [FollowListViewModel].
     *
     * @param modelClass Tipo de ViewModel requerido.
     * @return Instancia generada con las dependencias inyectadas.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FollowListViewModel(api, cache) as T
    }
}
