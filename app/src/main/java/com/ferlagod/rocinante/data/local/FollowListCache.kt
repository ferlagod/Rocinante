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
import com.ferlagod.rocinante.data.model.FollowUserItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestor de caché para las listas de seguimiento (seguidores y seguidos).
 * Almacena y recupera estas listas desde archivos locales en formato JSON en el directorio de caché de la app.
 *
 * @property context Contexto de la aplicación necesario para acceder al directorio de caché.
 */
class FollowListCache(private val context: Context) {
    private val gson = Gson()
    
    /**
     * Obtiene la referencia al archivo de caché según la dirección del seguimiento ("followers" o "following").
     *
     * @param direction Tipo de lista ("followers" o "following").
     * @return El archivo [File] correspondiente.
     */
    private fun getCacheFile(direction: String): File {
        return File(context.cacheDir, "follow_list_cache_$direction.json")
    }

    /**
     * Guarda de forma asíncrona en caché la lista de usuarios seguidos o seguidores.
     *
     * @param direction Tipo de lista ("followers" o "following").
     * @param list Lista de objetos [FollowUserItem] que se van a guardar.
     */
    suspend fun saveList(direction: String, list: List<FollowUserItem>) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(direction)
            val json = gson.toJson(list)
            file.writeText(json)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
    }

    /**
     * Carga de forma asíncrona de la caché la lista guardada de usuarios seguidos o seguidores.
     *
     * @param direction Tipo de lista a cargar ("followers" o "following").
     * @return Lista de [FollowUserItem] cargados o null si no existe el archivo o falla el parseo.
     */
    suspend fun loadList(direction: String): List<FollowUserItem>? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(direction)
            if (!file.exists()) return@withContext null
            val json = file.readText()
            val type = object : TypeToken<List<FollowUserItem>>() {}.type
            gson.fromJson<List<FollowUserItem>>(json, type)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            null
        }
    }
}
