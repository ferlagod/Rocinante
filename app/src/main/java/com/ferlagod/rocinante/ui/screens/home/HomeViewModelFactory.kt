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
import com.ferlagod.rocinante.data.api.BookWyrmProfile
import com.ferlagod.rocinante.data.local.TimelineCache
import com.ferlagod.rocinante.data.repository.BookWyrmRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Fábrica para la creación de instancias de [HomeViewModel].
 *
 * El [profileCache] reside aquí (y no dentro del repositorio) para que persista durante
 * toda la vida del factory, que a su vez se mantiene activo mientras el composable esté
 * en memoria gracias a [remember]. Esto evita que el caché se destruya en cada
 * recomposición de la pantalla.
 *
 * @property api Cliente REST de la API de BookWyrm.
 * @property timelineCache Proveedor de la caché local para persistencia temporal del timeline.
 */
class HomeViewModelFactory(
    private val api: BookWyrmApi,
    private val timelineCache: TimelineCache
) : ViewModelProvider.Factory {

    /**
     * Caché de perfiles de actores remotos compartida entre el factory y el repositorio.
     * Al residir en el factory (que es `remember`-ed en Compose), sobrevive recomposiciones
     * sin necesidad de re-descargar los perfiles de los usuarios seguidos.
     */
    private val profileCache: ConcurrentHashMap<String, BookWyrmProfile> = ConcurrentHashMap()

    /**
     * Crea una nueva instancia de [HomeViewModel] con un [BookWyrmRepository] que usa
     * el [profileCache] compartido.
     *
     * @param modelClass La clase del ViewModel a instanciar.
     * @return Una nueva instancia de [HomeViewModel].
     * @throws IllegalArgumentException si la clase no es reconocida.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val repository = BookWyrmRepository(api, profileCache)
            return HomeViewModel(repository, timelineCache) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}