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