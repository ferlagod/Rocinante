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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.progressSetupDataStore by preferencesDataStore(name = "ebook_pages_prefs")

/**
 * Cómo se anota el progreso de un libro *en este dispositivo*: en qué unidad y, si se lee en
 * ebook, cuántas páginas tiene.
 *
 * @property mode Unidad elegida, guardada como nombre del enum de la interfaz.
 * @property ebookPages Páginas del ebook, tal y como las ve el usuario en su lector.
 */
data class ProgressSetup(
    val mode: String? = null,
    val ebookPages: Int? = null
)

/**
 * Guarda la configuración de progreso de cada libro.
 *
 * BookWyrm solo conoce páginas y porcentaje, y la paginación de un ebook depende del tamaño de
 * letra del lector, así que ese dato es personal y no se envía al servidor: se usa para convertir
 * la página del ebook en el porcentaje que sí entiende BookWyrm. La unidad se recuerda para no
 * tener que elegirla en cada actualización.
 *
 * Todo se descarta cuando el libro pasa a «Leídos» (véase [clear]), porque a partir de ahí ya no
 * hay progreso que anotar.
 *
 * @property context Contexto de la aplicación usado para abrir el DataStore.
 */
class ProgressSetupStore(private val context: Context) {

    /**
     * Identificador del libro dentro de las claves. Se normaliza la URL/clave (sin barra final y
     * en minúsculas) para que la ficha abierta desde distintas pantallas apunte al mismo valor.
     */
    private fun idFor(bookKey: String) = bookKey.trim().trimEnd('/').lowercase()

    private fun pagesKey(bookKey: String) = intPreferencesKey("ebook_pages_" + idFor(bookKey))

    private fun modeKey(bookKey: String) = stringPreferencesKey("progress_mode_" + idFor(bookKey))

    private companion object {
        /** Última unidad usada en cualquier libro: sirve de propuesta al estrenar uno nuevo. */
        val KEY_LAST_MODE = stringPreferencesKey("progress_mode_last_used")
    }

    /**
     * Devuelve la configuración guardada para este libro.
     *
     * @param bookKey Clave del libro (su URL o id local).
     * @return La unidad y las páginas del ebook conocidas, con null en lo que aún no se sepa.
     */
    suspend fun get(bookKey: String): ProgressSetup {
        if (bookKey.isBlank()) return ProgressSetup()
        return runCatching {
            val prefs = context.progressSetupDataStore.data.first()
            ProgressSetup(mode = prefs[modeKey(bookKey)], ebookPages = prefs[pagesKey(bookKey)])
        }.getOrDefault(ProgressSetup())
    }

    /**
     * Última unidad usada en cualquier libro, para proponerla al anotar por primera vez el
     * progreso de otro: quien lee siempre en ebook no tiene que elegirlo libro tras libro.
     *
     * @return El nombre del último modo usado, o null si aún no se ha usado ninguno.
     */
    suspend fun getLastMode(): String? = runCatching {
        context.progressSetupDataStore.data.first()[KEY_LAST_MODE]
    }.getOrNull()

    /**
     * Recuerda la unidad en la que se anota el progreso de este libro, y la deja además como
     * última unidad usada.
     *
     * @param bookKey Clave del libro (su URL o id local).
     * @param mode Nombre del modo elegido.
     */
    suspend fun setMode(bookKey: String, mode: String) {
        if (bookKey.isBlank()) return
        runCatching {
            context.progressSetupDataStore.edit { prefs ->
                prefs[modeKey(bookKey)] = mode
                prefs[KEY_LAST_MODE] = mode
            }
        }
    }

    /**
     * Guarda el número de páginas del ebook de este libro.
     *
     * @param bookKey Clave del libro (su URL o id local).
     * @param pages Total de páginas del ebook.
     */
    suspend fun setEbookPages(bookKey: String, pages: Int) {
        if (bookKey.isBlank() || pages <= 0) return
        runCatching {
            context.progressSetupDataStore.edit { prefs -> prefs[pagesKey(bookKey)] = pages }
        }
    }

    /**
     * Olvida la configuración de este libro (al terminar de leerlo).
     *
     * @param bookKey Clave del libro (su URL o id local).
     */
    suspend fun clear(bookKey: String) {
        if (bookKey.isBlank()) return
        runCatching {
            context.progressSetupDataStore.edit { prefs ->
                prefs.remove(pagesKey(bookKey))
                prefs.remove(modeKey(bookKey))
            }
        }
    }
}
