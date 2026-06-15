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
import com.ferlagod.rocinante.data.model.TimelineUiItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sistema de caché para guardar el timeline principal del usuario en formato JSON.
 * También gestiona el almacenamiento local de las actividades a las que el usuario dio "Like".
 *
 * NOTA TÉCNICA: BookWyrm no expone actualmente (vía ActivityPub ni Mastodon API)
 * una colección de "favoritos" o "likes" del usuario (endpoints como /user/.../liked 
 * o /api/v1/favourites devuelven 404). Por lo tanto, el estado de los "me gusta"
 * solo puede persistir de forma local optimista en la app mediante SharedPreferences.
 * Si se borra la caché, estos estados visuales se pierden, aunque sigan existiendo en el servidor.
 *
 * @property context Contexto de la aplicación.
 */
class TimelineCache(private val context: Context) {
    private val gson = Gson()
    private val cacheFile by lazy { File(context.cacheDir, "timeline_cache.json") }

    /**
     * Guarda la lista de actividades del timeline en caché de forma asíncrona.
     *
     * @param timeline Lista de ítems [TimelineUiItem] a guardar.
     */
    suspend fun saveTimeline(timeline: List<TimelineUiItem>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(timeline)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
    }

    /**
     * Carga el timeline guardado de la caché.
     *
     * @return Lista de ítems [TimelineUiItem] o null si no hay caché disponible.
     */
    suspend fun loadTimeline(): List<TimelineUiItem>? = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.exists()) return@withContext null
            val json = cacheFile.readText()
            val type = object : TypeToken<List<TimelineUiItem>>() {}.type
            gson.fromJson<List<TimelineUiItem>>(json, type)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            null
        }
    }

    private val profileCacheFile by lazy { File(context.cacheDir, "profile_cache.json") }

    /**
     * Guarda el perfil del usuario en caché de forma asíncrona.
     */
    suspend fun saveProfile(profile: com.ferlagod.rocinante.data.api.BookWyrmProfile) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(profile)
            profileCacheFile.writeText(json)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
    }

    /**
     * Carga el perfil guardado de la caché.
     */
    suspend fun loadProfile(): com.ferlagod.rocinante.data.api.BookWyrmProfile? = withContext(Dispatchers.IO) {
        try {
            if (!profileCacheFile.exists()) return@withContext null
            val json = profileCacheFile.readText()
            gson.fromJson(json, com.ferlagod.rocinante.data.api.BookWyrmProfile::class.java)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            null
        }
    }

    /**
     * Guarda en SharedPreferences el conjunto de IDs de actividades a las que se les ha dado Like localmente.
     *
     * @param likedIds Set de IDs de los estados marcados como favoritos.
     */
    fun saveLikedStatuses(likedIds: Set<String>) {
        context.getSharedPreferences("liked_prefs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("liked_ids", likedIds)
            .apply()
    }

    /**
     * Carga de SharedPreferences el conjunto de IDs de actividades que han recibido Like localmente.
     *
     * @return Set con los identificadores de estados favoritos.
     */
    fun loadLikedStatuses(): Set<String> {
        return context.getSharedPreferences("liked_prefs", Context.MODE_PRIVATE)
            .getStringSet("liked_ids", emptySet()) ?: emptySet()
    }

    /**
     * Carga los libros guardados para una estantería específica.
     */
    suspend fun loadShelfBooks(slug: String): List<com.ferlagod.rocinante.data.api.ShelfBookItem>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "shelf_${slug}_cache.json")
            if (!file.exists()) return@withContext null
            val type = object : TypeToken<List<com.ferlagod.rocinante.data.api.ShelfBookItem>>() {}.type
            gson.fromJson<List<com.ferlagod.rocinante.data.api.ShelfBookItem>>(file.readText(), type)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Guarda la primera página de libros de una estantería en la caché.
     */
    suspend fun saveShelfBooks(slug: String, books: List<com.ferlagod.rocinante.data.api.ShelfBookItem>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "shelf_${slug}_cache.json")
            file.writeText(gson.toJson(books))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /**
     * Carga los usuarios sugeridos de la caché.
     */
    suspend fun loadSuggestedUsers(): List<com.ferlagod.rocinante.data.api.SuggestedUser>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "suggested_users_cache.json")
            if (!file.exists()) return@withContext null
            val type = object : TypeToken<List<com.ferlagod.rocinante.data.api.SuggestedUser>>() {}.type
            gson.fromJson<List<com.ferlagod.rocinante.data.api.SuggestedUser>>(file.readText(), type)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Guarda los usuarios sugeridos en la caché.
     */
    suspend fun saveSuggestedUsers(users: List<com.ferlagod.rocinante.data.api.SuggestedUser>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "suggested_users_cache.json")
            file.writeText(gson.toJson(users))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }
}
