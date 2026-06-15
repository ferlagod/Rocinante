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
