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
    val reminderMinute: Int = 0,
    val lastChangelogVersion: String = "",
    /**
     * Versión cuyas novedades ya se han dado por vistas desde el aviso de las notificaciones.
     * Va aparte de [lastChangelogVersion] a propósito: ese lo gasta el diálogo que sale al
     * abrir la app, y si fuera el mismo el aviso desaparecería en cuanto se cerrase, que es
     * justo lo que se quiere evitar.
     */
    val newsSeenVersion: String = "",
    /** Orden y visibilidad de los bloques del perfil, codificados por [ProfileLayout]. */
    val profileLayout: String = "",
    /** Orden y visibilidad de las estanterías de «Mis libros», codificados por [ShelfLayout]. */
    val shelfLayout: String = "",
    /** Si las tarjetas de las estanterías se agrupan arriba o abajo ([ShelfAlignment]). */
    val shelfAlignment: String = "",
    /**
     * Identificador de la estantería que hace de favoritos («favoritter-4337»), aprendido la
     * primera vez que se marca uno. Vacío mientras no haya ninguna; es la instancia la que
     * manda, así que se vuelve a buscar si deja de existir.
     */
    val favouriteShelf: String = ""
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
        private val KEY_LAST_CHANGELOG_VERSION = stringPreferencesKey("last_changelog_version")
        private val KEY_NEWS_SEEN_VERSION = stringPreferencesKey("news_seen_version")
        private val KEY_PROFILE_LAYOUT = stringPreferencesKey("profile_layout")
        private val KEY_SHELF_LAYOUT = stringPreferencesKey("shelf_layout")
        private val KEY_SHELF_ALIGNMENT = stringPreferencesKey("shelf_alignment")
        private val KEY_FAVOURITE_SHELF = stringPreferencesKey("favourite_shelf")
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                ThemeMode.SYSTEM
            }
            val openLinks = prefs[KEY_OPEN_LINKS] ?: false
            val reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false
            val reminderHour = prefs[KEY_REMINDER_HOUR] ?: 20
            val reminderMinute = prefs[KEY_REMINDER_MINUTE] ?: 0
            val lastChangelogVersion = prefs[KEY_LAST_CHANGELOG_VERSION] ?: ""
            val newsSeenVersion = prefs[KEY_NEWS_SEEN_VERSION] ?: ""
            val profileLayout = prefs[KEY_PROFILE_LAYOUT] ?: ""
            val shelfLayout = prefs[KEY_SHELF_LAYOUT] ?: ""
            val shelfAlignment = prefs[KEY_SHELF_ALIGNMENT] ?: ""
            val favouriteShelf = prefs[KEY_FAVOURITE_SHELF] ?: ""

            SettingsData(
                themeMode,
                openLinks,
                reminderEnabled,
                reminderHour,
                reminderMinute,
                lastChangelogVersion,
                newsSeenVersion,
                profileLayout,
                shelfLayout,
                shelfAlignment,
                favouriteShelf
            )
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

    /**
     * Guarda el orden y la visibilidad de los bloques del perfil.
     *
     * @param layout cadena generada por `ProfileLayout.encode()`.
     */
    suspend fun setProfileLayout(layout: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PROFILE_LAYOUT] = layout
        }
    }

    /**
     * Guarda la disposición de las estanterías de «Mis libros». El orden con la visibilidad y
     * la posición van juntos en una sola escritura: se deciden en el mismo diálogo y con una
     * sola pulsación de Guardar, así que la pantalla no debe llegar a ver medio cambio.
     *
     * @param layout cadena generada por `ShelfLayout.encode()`.
     * @param alignment id de `ShelfAlignment`.
     */
    suspend fun setShelfLayout(layout: String, alignment: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHELF_LAYOUT] = layout
            prefs[KEY_SHELF_ALIGNMENT] = alignment
        }
    }

    /**
     * Guarda la versión de la aplicación para la cual ya se ha mostrado el cuadro de diálogo de novedades.
     *
     * @param version String de la versión (ej. "1.0.4").
     */
    /** Guarda cuál es la estantería de favoritos, una vez encontrada o creada. */
    suspend fun setFavouriteShelf(identifier: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FAVOURITE_SHELF] = identifier
        }
    }

    suspend fun setLastChangelogVersion(version: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LAST_CHANGELOG_VERSION] = version
        }
    }

    /**
     * Da por vistas las novedades de [version] en el aviso de las notificaciones.
     */
    suspend fun setNewsSeenVersion(version: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NEWS_SEEN_VERSION] = version
        }
    }
}
