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

class SessionStorage(private val context: Context) {

    companion object {
        private val KEY_INSTANCE_URL = stringPreferencesKey("instance_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_COOKIE = stringPreferencesKey("cookie")
    }

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

    suspend fun saveSession(session: SessionData) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INSTANCE_URL] = session.instanceUrl
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_COOKIE] = session.cookie
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_INSTANCE_URL)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_COOKIE)
        }
    }
}