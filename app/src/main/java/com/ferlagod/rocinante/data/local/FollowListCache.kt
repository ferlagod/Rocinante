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
import com.ferlagod.rocinante.data.model.FollowUserItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FollowListCache(private val context: Context) {
    private val gson = Gson()
    
    private fun getCacheFile(direction: String): File {
        return File(context.cacheDir, "follow_list_cache_$direction.json")
    }

    suspend fun saveList(direction: String, list: List<FollowUserItem>) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(direction)
            val json = gson.toJson(list)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadList(direction: String): List<FollowUserItem>? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(direction)
            if (!file.exists()) return@withContext null
            val json = file.readText()
            val type = object : TypeToken<List<FollowUserItem>>() {}.type
            gson.fromJson<List<FollowUserItem>>(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
