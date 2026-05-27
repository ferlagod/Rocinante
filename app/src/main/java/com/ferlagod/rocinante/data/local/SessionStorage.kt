/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ferlagod.rocinante.data.model.SessionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

/**
 * Almacenamiento persistente y seguro para la sesión del usuario.
 * Utiliza DataStore Preferences para guardar las credenciales e información del servidor.
 *
 * @property context Contexto de la aplicación.
 */
class SessionStorage(private val context: Context) {

    companion object {
        private val KEY_INSTANCE_URL = stringPreferencesKey("instance_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_COOKIE = stringPreferencesKey("cookie")
    }

    /**
     * Flujo reactivo que emite los datos de sesión activa [SessionData].
     * Si no hay sesión válida iniciada, emite null.
     */
    val sessionFlow: Flow<SessionData?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs: Preferences ->
            val instanceUrl = prefs[KEY_INSTANCE_URL]
            val username = prefs[KEY_USERNAME]
            val cookie = prefs[KEY_COOKIE]

            if (
                instanceUrl.isNullOrBlank() ||
                username.isNullOrBlank() ||
                cookie.isNullOrBlank()
            ) {
                null
            } else {
                SessionData(
                    instanceUrl = instanceUrl,
                    username = username,
                    cookie = cookie
                )
            }
        }

    /**
     * Almacena de forma persistente la información de una nueva sesión.
     *
     * @param session Datos de la sesión a guardar.
     */
    suspend fun saveSession(session: SessionData) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INSTANCE_URL] = session.instanceUrl
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_COOKIE] = session.cookie
        }
    }

    /**
     * Elimina todos los datos de sesión de la memoria persistente (Cierre de sesión).
     */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_INSTANCE_URL)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_COOKIE)
        }
    }
}