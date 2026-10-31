package com.ferlagod.rocinante.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class InstanceUrlTest {

    @Test
    fun `normaliza lo que se suele escribir o pegar`() {
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("bookwyrm.social"))
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("  BookWyrm.Social/ "))
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("https://bookwyrm.social/login"))
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("@yo@bookwyrm.social"))
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("yo@bookwyrm.social"))
        assertEquals("https://bookwyrm.social", InstanceUrl.normalize("https://bookwyrm.social/user/yo?x=1"))
        assertEquals("http://localhost:8000", InstanceUrl.normalize("http://localhost:8000/"))
        assertEquals("", InstanceUrl.normalize("   "))
    }
}
