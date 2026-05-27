/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

/**
 * Modos de tema visual soportados por la aplicación.
 */
enum class ThemeMode {
    /** Tema determinado por la configuración del sistema operativo. */
    SYSTEM,
    /** Tema claro. */
    LIGHT,
    /** Tema oscuro. */
    DARK
}

/**
 * Representa los ajustes de configuración local de la aplicación.
 *
 * @property themeMode El tema visual seleccionado por el usuario.
 * @property openLinksExternally Indica si los enlaces externos deben abrirse en el navegador web del sistema.
 * @property reminderEnabled Determina si las notificaciones de recordatorio de lectura están activas.
 * @property reminderHour Hora diaria configurada para recibir la notificación.
 * @property reminderMinute Minuto configurado para recibir la notificación.
 */
data class SettingsData(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val openLinksExternally: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
)

/**
 * Gestor de almacenamiento de configuración de usuario persistente usando DataStore Preferences.
 *
 * @property context Contexto de la aplicación utilizado para inicializar DataStore.
 */
class SettingsPreferences(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_OPEN_LINKS = booleanPreferencesKey("open_links_externally")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_HOUR = androidx.datastore.preferences.core.intPreferencesKey("reminder_hour")
        private val KEY_REMINDER_MINUTE = androidx.datastore.preferences.core.intPreferencesKey("reminder_minute")
    }

    /**
     * Flujo reactivo que emite los ajustes de configuración de la aplicación cada vez que cambian.
     */
    val settingsFlow: Flow<SettingsData> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeStr = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name
            val themeMode = try {
                ThemeMode.valueOf(themeStr)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
            val openLinks = prefs[KEY_OPEN_LINKS] ?: false
            val reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false
            val reminderHour = prefs[KEY_REMINDER_HOUR] ?: 20
            val reminderMinute = prefs[KEY_REMINDER_MINUTE] ?: 0

            SettingsData(themeMode, openLinks, reminderEnabled, reminderHour, reminderMinute)
        }

    /**
     * Guarda el modo de tema visual preferido por el usuario de forma persistente.
     *
     * @param mode El tema a guardar.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME] = mode.name
        }
    }

    /**
     * Guarda la preferencia del usuario sobre si se abren los enlaces de forma externa en el navegador del sistema.
     *
     * @param open Si es true, abrirá enlaces externamente.
     */
    suspend fun setOpenLinksExternally(open: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_OPEN_LINKS] = open
        }
    }

    /**
     * Activa o desactiva la función de recordatorio diario de lectura.
     *
     * @param enabled Si es true, habilita el recordatorio.
     */
    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_REMINDER_ENABLED] = enabled
        }
    }

    /**
     * Actualiza la hora del recordatorio diario configurada por el usuario.
     *
     * @param hour Hora en formato de 24 horas (0-23).
     * @param minute Minuto (0-59).
     */
    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_REMINDER_HOUR] = hour
            prefs[KEY_REMINDER_MINUTE] = minute
        }
    }
}
