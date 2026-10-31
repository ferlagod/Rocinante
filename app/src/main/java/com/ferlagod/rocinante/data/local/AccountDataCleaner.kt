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
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.model.SessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Borra lo guardado de una cuenta cuando entra otra.
 *
 * Las cachés en disco (timeline, estanterías, favoritos, enriquecimiento con las notas y fechas
 * de lectura del usuario...) tienen nombres fijos, sin usuario ni instancia. Al cambiar de
 * cuenta o de servidor se veían los datos de la anterior.
 *
 * No se borran al cerrar sesión, sino al entrar con una cuenta distinta de la que las generó:
 * si la sesión caduca y vuelve a entrar el mismo usuario, conserva sus estadísticas y datos
 * enriquecidos, que cuesta muchas peticiones volver a reunir.
 */
object AccountDataCleaner {

    private const val PREFS = "account_owner_prefs"
    private const val KEY_OWNER = "cache_owner"

    /** Carpetas de [Context.getCacheDir] con datos de la cuenta, además de los `*.json` sueltos. */
    private val ACCOUNT_DIRS = setOf("authors", "book_details")

    /** Clave de cuenta: instancia y usuario, sin esquema ni mayúsculas. */
    internal fun ownerKey(session: SessionData): String {
        val host = session.instanceUrl.trim().lowercase()
            .removePrefix("http://").removePrefix("https://")
            .substringBefore("/").removePrefix("www.")
        val user = session.username.trim().lowercase().removePrefix("@").substringBefore("@")
        return "$user@$host"
    }

    /**
     * Antes de guardar la sesión nueva: si las cachés son de otra cuenta, se borran. Si no se
     * sabe de quién son (instalaciones anteriores a este cambio), se dejan y se apunta la dueña.
     */
    suspend fun prepareFor(context: Context, session: SessionData, onCleared: () -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val newOwner = ownerKey(session)
            val previousOwner = prefs.getString(KEY_OWNER, null)
            if (previousOwner != null && previousOwner != newOwner) {
                clearAccountData(context)
                onCleared()
            }
            prefs.edit().putString(KEY_OWNER, newOwner).commit()
        }

    private suspend fun clearAccountData(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                when {
                    file.isFile && file.name.endsWith(".json") -> file.delete()
                    file.isDirectory && file.name in ACCOUNT_DIRS -> file.deleteRecursively()
                }
            }
        } catch (_: Exception) {}
        NetworkClient.evictHttpCache()
        BookWyrmScraper.invalidateBookPage()
    }
}
