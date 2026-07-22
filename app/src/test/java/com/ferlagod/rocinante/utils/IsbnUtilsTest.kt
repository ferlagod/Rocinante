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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias puras de [IsbnUtils]. No dependen de Android, así que corren en la
 * JVM con la JUnit ya presente en el proyecto (`./gradlew :app:testDebugUnitTest`).
 */
class IsbnUtilsTest {

    // --- isIsbn: casos válidos ---

    @Test
    fun `ISBN-13 de 13 digitos es ISBN`() {
        assertTrue(IsbnUtils.isIsbn("9783161484100"))
    }

    @Test
    fun `ISBN-13 con guiones se reconoce`() {
        assertTrue(IsbnUtils.isIsbn("978-3-16-148410-0"))
    }

    @Test
    fun `ISBN-13 con prefijo y espacios se reconoce`() {
        assertTrue(IsbnUtils.isIsbn("ISBN 978 0 306 40615 7"))
    }

    @Test
    fun `ISBN-10 de 10 digitos es ISBN`() {
        assertTrue(IsbnUtils.isIsbn("0306406152"))
    }

    @Test
    fun `ISBN-10 terminado en X mayuscula es ISBN`() {
        assertTrue(IsbnUtils.isIsbn("097522980X"))
    }

    @Test
    fun `ISBN-10 terminado en x minuscula se normaliza y es ISBN`() {
        assertTrue(IsbnUtils.isIsbn("097522980x"))
    }

    @Test
    fun `ISBN-10 con guiones se reconoce`() {
        assertTrue(IsbnUtils.isIsbn("0-9752298-0-X"))
    }

    // --- isIsbn: casos rechazados ---

    @Test
    fun `12 digitos no es ISBN`() {
        assertFalse(IsbnUtils.isIsbn("123456789012"))
    }

    @Test
    fun `14 digitos no es ISBN`() {
        assertFalse(IsbnUtils.isIsbn("12345678901234"))
    }

    @Test
    fun `cadena vacia no es ISBN`() {
        assertFalse(IsbnUtils.isIsbn(""))
    }

    @Test
    fun `texto de titulo no es ISBN`() {
        assertFalse(IsbnUtils.isIsbn("Dune"))
    }

    @Test
    fun `contenido de QR no numerico no es ISBN`() {
        assertFalse(IsbnUtils.isIsbn("https://example.com/page"))
    }

    @Test
    fun `13 caracteres con una X no es un ISBN-13 valido`() {
        // Un EAN-13 de libro es siempre numérico; una 'X' incrustada lo invalida.
        assertFalse(IsbnUtils.isIsbn("978316148410X"))
    }

    @Test
    fun `X fuera de la ultima posicion en un ISBN-10 se rechaza`() {
        assertFalse(IsbnUtils.isIsbn("X975229800"))
    }

    // --- normalize ---

    @Test
    fun `normalize elimina guiones y espacios`() {
        assertEquals("9783161484100", IsbnUtils.normalize("978-3-16-148410-0"))
    }

    @Test
    fun `normalize pasa la x de control a mayuscula`() {
        assertEquals("097522980X", IsbnUtils.normalize("0-9752298-0-x"))
    }

    @Test
    fun `normalize descarta letras que no son X`() {
        assertEquals("9780306406157", IsbnUtils.normalize("ISBN 978-0-306-40615-7"))
    }

    @Test
    fun `normalize de cadena sin digitos devuelve vacio`() {
        assertEquals("", IsbnUtils.normalize("Dune"))
    }
}
