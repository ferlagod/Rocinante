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

class TimelineCache(private val context: Context) {
    private val gson = Gson()
    private val cacheFile by lazy { File(context.cacheDir, "timeline_cache.json") }

    suspend fun saveTimeline(timeline: List<TimelineUiItem>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(timeline)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    fun saveLikedStatuses(likedIds: Set<String>) {
        context.getSharedPreferences("liked_prefs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("liked_ids", likedIds)
            .apply()
    }

    fun loadLikedStatuses(): Set<String> {
        return context.getSharedPreferences("liked_prefs", Context.MODE_PRIVATE)
            .getStringSet("liked_ids", emptySet()) ?: emptySet()
    }
}
