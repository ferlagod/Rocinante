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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El recorrido de las páginas de una estantería tiene que parar aunque la instancia nunca dé
 * una página vacía: el paginador de Django repite la última.
 */
class ShelfPagesTest {

    /** Una estantería de [pages] páginas que, pasada la última, la repite como hace Django. */
    private fun shelf(vararg pages: List<String>): Pair<suspend (Int) -> List<String>, () -> Int> {
        var requests = 0
        val fetch: suspend (Int) -> List<String> = { page ->
            requests++
            pages[minOf(page, pages.size) - 1]
        }
        return fetch to { requests }
    }

    @Test
    fun `para cuando la página repite la última`() = runBlocking {
        val (fetch, requests) = shelf(listOf("a", "b"), listOf("c"))
        val ids = FavouriteShelf.walkShelfPages(fetch)
        assertEquals(setOf("a", "b", "c"), ids)
        // Dos páginas de verdad y una más para ver que no hay nada nuevo; antes eran veinte.
        assertEquals(3, requests())
    }

    @Test
    fun `para con una página vacía`() = runBlocking {
        val (fetch, requests) = shelf(listOf("a"), emptyList())
        assertEquals(setOf("a"), FavouriteShelf.walkShelfPages(fetch))
        assertEquals(2, requests())
    }

    @Test
    fun `deja de pedir en cuanto quien llama lo encuentra`() = runBlocking {
        val (fetch, requests) = shelf(listOf("a"), listOf("b"), listOf("c"))
        FavouriteShelf.walkShelfPages(fetch) { ids -> "b" !in ids }
        assertEquals(2, requests())
    }
}
