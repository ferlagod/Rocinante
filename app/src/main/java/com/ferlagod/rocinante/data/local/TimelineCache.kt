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
}
