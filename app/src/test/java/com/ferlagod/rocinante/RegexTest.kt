package com.ferlagod.rocinante

import org.junit.Test
import org.junit.Assert.assertEquals

class RegexTest {
    @Test
    fun testRegex() {
        val html = "<input type=\"hidden\" name=\"user\" value=\"123\">"
        val fieldName = "user"
        val nameFirst = """name=["']${Regex.escape(fieldName)}["'][^>]*?value=["']([^"']+)["']""".toRegex()
        val match = nameFirst.find(html)
        assertEquals("123", match?.groupValues?.get(1))
    }
}
