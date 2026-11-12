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
package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CreateBookFormTest {

    private fun utc(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `el idioma del movil se traduce al codigo del formulario`() {
        assertEquals("spa", CreateBookForm.languageCodeFor("es"))
        assertEquals("cat", CreateBookForm.languageCodeFor("ca-ES"))
        assertEquals("por", CreateBookForm.languageCodeFor("pt_BR"))
        assertNull(CreateBookForm.languageCodeFor("ja"))
        assertNull(CreateBookForm.languageCodeFor(null))
    }

    @Test
    fun `la fecha dia mes año pasa a ISO y lo demas se queda igual`() {
        assertEquals("1967-05-30", CreateBookForm.normalizePublishedDate(" 30/5/1967 "))
        assertEquals("1967-05-03", CreateBookForm.normalizePublishedDate("3-05-1967"))
        assertEquals("1967", CreateBookForm.normalizePublishedDate("1967"))
        assertEquals("1967-05", CreateBookForm.normalizePublishedDate("1967-05"))
    }

    @Test
    fun `el calendario se abre en la fecha escrita`() {
        assertEquals(utc(1967, 1, 1), CreateBookForm.publishedDateToUtcMillis("1967"))
        assertEquals(utc(1967, 5, 1), CreateBookForm.publishedDateToUtcMillis("1967-05"))
        assertEquals(utc(1967, 5, 30), CreateBookForm.publishedDateToUtcMillis("1967-05-30"))
        assertEquals(utc(1967, 5, 30), CreateBookForm.publishedDateToUtcMillis("30/05/1967"))
        assertNull(CreateBookForm.publishedDateToUtcMillis(""))
        assertNull(CreateBookForm.publishedDateToUtcMillis("1967-13-01"))
        assertNull(CreateBookForm.publishedDateToUtcMillis("abc"))
    }

    @Test
    fun `los autores casan sin tildes ni orden y sin repetirse`() {
        val candidates = listOf("Gabriel García Márquez", null, "Gabriel Garcia Marquez", "Gabriela Mistral", " ", "Márquez, Gabriel García")
        assertEquals(
            listOf("Gabriel García Márquez", "Márquez, Gabriel García"),
            CreateBookForm.matchingAuthors("garcia marquez", candidates)
        )
    }

    @Test
    fun `no se sugiere lo que ya esta escrito ni nada si no se escribe nada`() {
        assertEquals(emptyList<String>(), CreateBookForm.matchingAuthors("Gabriela Mistral", listOf("Gabriela Mistral")))
        assertEquals(emptyList<String>(), CreateBookForm.matchingAuthors("  ", listOf("Gabriela Mistral")))
        assertEquals(1, CreateBookForm.matchingAuthors("a", listOf("Ana", "Andrés", "Alba"), limit = 1).size)
    }
}
