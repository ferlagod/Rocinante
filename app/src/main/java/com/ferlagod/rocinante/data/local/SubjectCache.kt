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
import com.ferlagod.rocinante.data.model.OpenLibrarySubject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Guarda las materias que Open Library va contestando, para no volver a preguntárselas.
 *
 * Se cachean porque el catálogo de materias apenas se mueve —una materia nueva no aparece de un
 * día para otro— y porque quien busca escribe hacia delante y hacia atrás sobre lo mismo. Pero lo
 * que de verdad lo justifica es que Open Library se cae a rachas: con lo ya preguntado en disco,
 * elegir una materia que uno ya usó sigue funcionando el día que no contestan.
 *
 * Guarda dos cosas. Lo preguntado, que caduca; y lo elegido, que no: si uno escogió «Historical
 * fiction» una vez, esa materia le sirve para siempre y es lo que se le enseña de entrada.
 */
class SubjectCache(private val context: Context) {

    private val gson = Gson()
    private val file by lazy { File(context.cacheDir, "openlibrary_subjects_cache.json") }

    /**
     * Cuánto vale lo preguntado. Un mes es mucho para un caché y aquí es lo correcto: lo que se
     * pierde por tener la lista vieja es una materia recién creada, y lo que se gana es no
     * depender de que un servidor lento conteste.
     */
    private val ttlMs = TimeUnit.DAYS.toMillis(30)

    /** Cuántas búsquedas distintas se recuerdan, y cuántas materias elegidas. */
    private val maxQueries = 200
    private val maxRecent = 40

    private data class Entry(
        val subjects: List<OpenLibrarySubject> = emptyList(),
        val savedAt: Long = 0
    )

    private data class Store(
        val queries: MutableMap<String, Entry> = mutableMapOf(),
        val recent: MutableList<OpenLibrarySubject> = mutableListOf()
    )

    // Copia en memoria para no leer el disco en cada tecla. Se llena en la primera lectura.
    private var memoria: Store? = null

    private suspend fun store(): Store = withContext(Dispatchers.IO) {
        memoria?.let { return@withContext it }
        val leido = try {
            if (file.exists()) {
                gson.fromJson<Store>(file.readText(), object : TypeToken<Store>() {}.type)
            } else null
        } catch (e: Exception) {
            // Un caché ilegible se tira: es más barato volver a preguntar que arrastrar basura.
            null
        } ?: Store()
        memoria = leido
        leido
    }

    private suspend fun persist(s: Store) = withContext(Dispatchers.IO) {
        try {
            file.writeText(gson.toJson(s))
        } catch (e: Exception) {
            // Quedarse sin caché no puede romper la búsqueda.
        }
    }

    /** La misma búsqueda escrita de dos formas es la misma búsqueda. */
    private fun key(query: String) = query.trim().lowercase()

    /** Lo que se contestó a esa búsqueda, o null si no se preguntó nunca o ya caducó. */
    suspend fun lookup(query: String): List<OpenLibrarySubject>? {
        val entry = store().queries[key(query)] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > ttlMs) return null
        return entry.subjects
    }

    /** Apunta lo que contestó Open Library. */
    suspend fun save(query: String, subjects: List<OpenLibrarySubject>) {
        val s = store()
        s.queries[key(query)] = Entry(subjects, System.currentTimeMillis())
        if (s.queries.size > maxQueries) {
            // Fuera las más viejas. Sin esto el fichero crece con cada letra que alguien escriba.
            val sobran = s.queries.entries.sortedBy { it.value.savedAt }
                .take(s.queries.size - maxQueries)
            sobran.forEach { s.queries.remove(it.key) }
        }
        persist(s)
    }

    /** Las materias que uno ha elegido, de la última a la primera. */
    suspend fun recent(): List<OpenLibrarySubject> = store().recent.toList()

    /** Apunta una materia elegida, que pasa al principio de la lista. */
    suspend fun remember(subject: OpenLibrarySubject) {
        val s = store()
        s.recent.removeAll { it.key == subject.key }
        s.recent.add(0, subject)
        while (s.recent.size > maxRecent) s.recent.removeAt(s.recent.size - 1)
        persist(s)
    }
}
