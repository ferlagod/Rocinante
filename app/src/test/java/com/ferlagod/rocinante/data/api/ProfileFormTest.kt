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
package com.ferlagod.rocinante.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El formulario de preferencias del perfil se devuelve entero: si una de sus listas viaja
 * vacía, BookWyrm lo rechaza y el perfil no se guarda.
 */
class ProfileFormTest {

    // Lo esencial de preferences/edit_user.html, con un formulario POST de otra cosa delante.
    private val html = """
        <html><body>
        <form name="logout" method="post" action="/logout/">
            <input type="hidden" name="csrfmiddlewaretoken" value="abc">
        </form>
        <form name="edit-profile" action="/preferences/profile" method="post" enctype="multipart/form-data">
            <input type="hidden" name="csrfmiddlewaretoken" value="abc">
            <input type="file" name="avatar">
            <input type="text" name="name" value="Viejo">
            <textarea name="summary">Bio vieja</textarea>
            <input type="email" name="email" value="yo@example.com">
            <input type="checkbox" name="show_goal" checked>
            <input type="checkbox" name="discoverable">
            <select name="preferred_timezone">
                <option value="UTC">UTC</option>
                <option value="Europe/Madrid" selected>Europe/Madrid</option>
            </select>
            <select name="default_post_privacy">
                <option value="public">Public</option>
                <option value="followers" selected>Followers</option>
            </select>
            <select name="preferred_language">
                <option value="">Sin preferencia</option>
                <option value="es-es" selected>Español</option>
            </select>
            <select name="theme">
                <option value="1">Claro</option>
                <option value="2">Oscuro</option>
            </select>
        </form>
        </body></html>
    """.trimIndent()

    private val fields = BookWyrmScraper.buildProfileFormFields(html, "Nuevo", "Bio nueva")!!.toMap()

    @Test
    fun `las listas se envían con la opción marcada`() {
        assertEquals("Europe/Madrid", fields["preferred_timezone"])
        assertEquals("followers", fields["default_post_privacy"])
        assertEquals("es-es", fields["preferred_language"])
    }

    @Test
    fun `una lista sin opción marcada se envía con la primera`() {
        assertEquals("1", fields["theme"])
    }

    @Test
    fun `el nombre y la biografía se cambian y lo demás se conserva`() {
        assertEquals("Nuevo", fields["name"])
        assertEquals("Bio nueva", fields["summary"])
        assertEquals("yo@example.com", fields["email"])
    }

    @Test
    fun `las casillas sin marcar y la foto no se envían`() {
        assertEquals("on", fields["show_goal"])
        assertFalse(fields.containsKey("discoverable"))
        assertFalse(fields.containsKey("avatar"))
    }

    @Test
    fun `el token lo pone quien envía, no la página`() {
        assertFalse(fields.containsKey("csrfmiddlewaretoken"))
    }

    @Test
    fun `sin el formulario de perfil devuelve null`() {
        assertNull(BookWyrmScraper.buildProfileFormFields("<form method=post></form>", "a", "b"))
    }
}
