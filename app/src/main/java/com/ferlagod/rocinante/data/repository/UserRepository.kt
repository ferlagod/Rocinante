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
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.BookWyrmProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext


class UserRepository(
    private val api: BookWyrmApi,
    val profileCache: java.util.concurrent.ConcurrentHashMap<String, BookWyrmProfile> =
        java.util.concurrent.ConcurrentHashMap()
) {
    /**
     * Carga el perfil completo de un usuario de BookWyrm, incluyendo sus contadores de seguidores
     * y seguidos realizando consultas en paralelo.
     *
     * @param username Nombre del usuario a cargar (se limpia el prefijo '@').
     * @return El objeto [BookWyrmProfile] con la información del perfil.
     */
    suspend fun loadProfile(username: String): BookWyrmProfile = withContext(Dispatchers.IO) {
        val cleanUsername = username.removePrefix("@").substringBefore("@").trim()

        // 1. Descarga del perfil base
        val profile = api.getUserProfile(cleanUsername)

        // 2. Corrutinas para descargar contadores en paralelo asegurando el formato JSON
        coroutineScope {
            val followersDeferred = profile.followers?.let { url ->
                async {
                    try {
                        api.getCollectionData(url).totalItems
                    } catch (_: Exception) { null }
                }
            }

            val followingDeferred = profile.following?.let { url ->
                async {
                    try {
                        api.getCollectionData(url).totalItems
                    } catch (_: Exception) { null }
                }
            }
            
            val readingGoalDeferred = async {
                try {
                    val profileUrl = profile.id ?: return@async null
                    // BookWyrm JSON id is usually the profile URL. If it ends with .json, strip it.
                    val htmlUrl = profileUrl.removeSuffix(".json")
                    val htmlResponse = api.getRawHtmlResponse(htmlUrl)
                    val htmlString = htmlResponse.body()?.string() ?: return@async null
                    val doc = org.jsoup.Jsoup.parse(htmlString)
                    val progressElement = doc.selectFirst("progress")
                    if (progressElement != null) {
                        val value = progressElement.attr("value").toIntOrNull()
                        val max = progressElement.attr("max").toIntOrNull()
                        if (value != null && max != null) {
                            com.ferlagod.rocinante.data.model.ReadingGoal(max = max, value = value)
                        } else null
                    } else null
                } catch (_: Exception) { null }
            }

            // 3. Asignación de resultados reales
            profile.followersCountLocal = followersDeferred?.await()
            profile.followingCountLocal = followingDeferred?.await()
            profile.readingGoal = readingGoalDeferred.await()
        }

        profile
    }
    /**
     * Obtiene el ID numérico de la base de datos de un usuario a partir de su URL de perfil,
     * parseando el HTML de una de sus estanterías de libros.
     *
     * @param profileUrl URL de perfil del usuario.
     * @return El ID del usuario, o null si falla.
     */
    suspend fun getUserId(profileUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val shelfUrl = "$profileUrl/books/to-read"
                val response = api.getRawHtmlResponse(shelfUrl)
                if (response.isSuccessful) {
                    val html = response.body()?.string() ?: ""
                    val userMatch = "name=[\"']user[\"'][^>]*?value=[\"'](\\d+)[\"']|value=[\"'](\\d+)[\"'][^>]*?name=[\"']user[\"']".toRegex(RegexOption.IGNORE_CASE).find(html)
                    userMatch?.let { it.groups[1]?.value ?: it.groups[2]?.value }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
