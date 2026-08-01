package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLayoutTest {

    @Test
    fun `sin nada guardado se usa el orden por defecto`() {
        assertEquals(ProfileLayout.DEFAULT, ProfileLayout.decode(null))
        assertEquals(ProfileLayout.DEFAULT, ProfileLayout.decode(""))
        assertEquals(ProfileLayout.DEFAULT, ProfileLayout.decode("   "))
    }

    @Test
    fun `lo guardado se vuelve a leer igual`() {
        val layout = ProfileLayout.DEFAULT
            .toggled(ProfileSection.FORMATS)
            .copy(sections = ProfileLayout.DEFAULT.sections.reversed())

        assertEquals(layout, ProfileLayout.decode(layout.encode()))
    }

    @Test
    fun `un bloque nuevo aparece aunque no estuviera guardado`() {
        // Disposición vieja, de cuando solo existían dos bloques.
        val stored = "bio,currently_reading"

        val layout = ProfileLayout.decode(stored)

        assertEquals(ProfileSection.BIO, layout.sections[0])
        assertEquals(ProfileSection.CURRENTLY_READING, layout.sections[1])
        // El resto se añade al final y visible, en vez de desaparecer del perfil.
        assertEquals(ProfileSection.entries.size, layout.sections.size)
        assertTrue(layout.hidden.isEmpty())
        assertTrue(layout.visibleSections.containsAll(ProfileSection.entries))
    }

    @Test
    fun `un bloque retirado en el futuro se ignora sin romper el resto`() {
        val layout = ProfileLayout.decode("bio,bloque_que_ya_no_existe,-formats")

        assertEquals(ProfileSection.BIO, layout.sections[0])
        assertEquals(ProfileSection.FORMATS, layout.sections[1])
        assertTrue(ProfileSection.FORMATS in layout.hidden)
        assertEquals(ProfileSection.entries.size, layout.sections.size)
    }

    @Test
    fun `los ocultos conservan su sitio para cuando se reactiven`() {
        val hiddenLayout = ProfileLayout.DEFAULT.toggled(ProfileSection.RATINGS)
        assertFalse(hiddenLayout.isVisible(ProfileSection.RATINGS))
        assertFalse(ProfileSection.RATINGS in hiddenLayout.visibleSections)

        val restored = ProfileLayout.decode(hiddenLayout.encode()).toggled(ProfileSection.RATINGS)
        assertTrue(restored.isVisible(ProfileSection.RATINGS))
        assertEquals(
            ProfileLayout.DEFAULT.sections.indexOf(ProfileSection.RATINGS),
            restored.sections.indexOf(ProfileSection.RATINGS)
        )
    }

    @Test
    fun `un id repetido no duplica el bloque`() {
        val layout = ProfileLayout.decode("bio,bio,-bio")

        assertEquals(1, layout.sections.count { it == ProfileSection.BIO })
        assertTrue(layout.isVisible(ProfileSection.BIO))
    }

    @Test
    fun `los ids guardados no deben cambiar entre versiones`() {
        // Si esta prueba falla es que se ha renombrado un id: las disposiciones ya guardadas
        // en los dispositivos dejarían de reconocerse y el bloque se movería al final.
        assertEquals(
            listOf(
                "reading_stats", "currently_reading", "to_read", "bio", "reading_goal",
                "top_rated", "top_authors", "ratings", "languages", "formats", "suggested_users"
            ),
            ProfileSection.entries.map { it.id }
        )
    }
}
