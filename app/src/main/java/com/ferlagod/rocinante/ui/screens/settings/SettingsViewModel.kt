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
package com.ferlagod.rocinante.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ferlagod.rocinante.data.local.SettingsData
import com.ferlagod.rocinante.data.local.SettingsPreferences
import com.ferlagod.rocinante.data.local.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel responsable de la gestión y persistencia de las preferencias de configuración del usuario.
 * Interactúa con [SettingsPreferences] para leer y escribir opciones como el tema, recordatorios y apertura de enlaces.
 *
 * @property settingsPreferences Almacén local de preferencias de configuración (DataStore).
 */
class SettingsViewModel(
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    /**
     * Estado reactivo que expone la configuración actual a la interfaz de usuario.
     */
    val settingsState: StateFlow<SettingsData> = settingsPreferences.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsData()
        )

    /**
     * Actualiza el modo de tema visual preferido por el usuario (claro, oscuro, sistema).
     *
     * @param mode El nuevo modo de tema a aplicar.
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsPreferences.setThemeMode(mode)
        }
    }

    /**
     * Alterna la preferencia para abrir los enlaces fuera de la aplicación mediante el navegador por defecto.
     *
     * @param open `true` si los enlaces deben abrirse externamente.
     */
    fun setOpenLinksExternally(open: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setOpenLinksExternally(open)
        }
    }

    /**
     * Habilita o deshabilita las notificaciones diarias de recordatorio de lectura.
     *
     * @param enabled `true` para activar los recordatorios.
     */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setReminderEnabled(enabled)
        }
    }

    /**
     * Configura la hora y minuto del día para el recordatorio de lectura.
     *
     * @param hour Hora del recordatorio (formato 24h).
     * @param minute Minuto del recordatorio.
     */
    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsPreferences.setReminderTime(hour, minute)
        }
    }
}

/**
 * Fábrica para instanciar [SettingsViewModel] inyectando sus dependencias necesarias.
 *
 * @property settingsPreferences Almacenamiento local de configuración.
 */
class SettingsViewModelFactory(
    private val settingsPreferences: SettingsPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
