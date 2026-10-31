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

class LanguageFlagsTest {

    @Test
    fun `mapea idiomas comunes a sus banderas`() {
        assertEquals("🇬🇧", LanguageFlags.flagFor("english"))
        assertEquals("🇪🇸", LanguageFlags.flagFor("español"))
        assertEquals("🇫🇷", LanguageFlags.flagFor("french"))
    }

    @Test
    fun `mapea estonio y esperanto`() {
        assertEquals("🇪🇪", LanguageFlags.flagFor("estonian"))
        assertEquals("🇪🇪", LanguageFlags.flagFor("eesti"))
        assertEquals("🟢", LanguageFlags.flagFor("esperanto"))
    }

    @Test
    fun `ignora mayusculas y espacios`() {
        assertEquals("🇪🇪", LanguageFlags.flagFor("  Estonian  "))
        assertEquals("🟢", LanguageFlags.flagFor("ESPERANTO"))
    }

    @Test
    fun `devuelve null para idiomas vacios o desconocidos`() {
        assertNull(LanguageFlags.flagFor(null))
        assertNull(LanguageFlags.flagFor(""))
        assertNull(LanguageFlags.flagFor("   "))
        assertNull(LanguageFlags.flagFor("desconocido_12345"))
    }
}
