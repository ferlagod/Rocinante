package com.ferlagod.rocinante.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostPrivacyTest {

    private fun page(options: String) = """
        <html><body><form name="edit-profile" method="post">
          <select name="default_post_privacy" id="id_default_post_privacy">$options</select>
          <select name="preferred_timezone"><option value="UTC" selected>UTC</option></select>
        </form></body></html>
    """.trimIndent()

    @Test
    fun `lee la opción marcada del formulario de preferencias`() {
        val html = page(
            """
            <option value="public">Public</option>
            <option value="unlisted">Unlisted</option>
            <option value="followers" selected>Followers</option>
            <option value="direct">Private</option>
            """
        )
        assertEquals("followers", PostPrivacy.parse(html))
    }

    @Test
    fun `sin opción marcada vale la primera, como en el navegador`() {
        assertEquals("public", PostPrivacy.parse(page("""<option value="public">P</option><option value="direct">D</option>""")))
    }

    @Test
    fun `sin el formulario o con un valor desconocido no se inventa nada`() {
        assertNull(PostPrivacy.parse(""))
        assertNull(PostPrivacy.parse("<html><body><p>Login</p></body></html>"))
        assertNull(PostPrivacy.parse(page("""<option value="everyone" selected>?</option>""")))
    }
}
