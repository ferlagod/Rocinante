package com.ferlagod.rocinante.data.local

import com.ferlagod.rocinante.data.model.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountDataCleanerTest {

    @Test
    fun `la misma cuenta escrita de otra forma es la misma duena`() {
        val a = AccountDataCleaner.ownerKey(SessionData("https://BookWyrm.social", "Yo", "c"))
        val b = AccountDataCleaner.ownerKey(SessionData("bookwyrm.social/", "@yo@bookwyrm.social", "d"))
        assertEquals(a, b)
    }

    @Test
    fun `otra instancia u otro usuario es otra duena`() {
        val a = AccountDataCleaner.ownerKey(SessionData("https://bookwyrm.social", "yo", "c"))
        assertNotEquals(a, AccountDataCleaner.ownerKey(SessionData("https://bookwyrm.it", "yo", "c")))
        assertNotEquals(a, AccountDataCleaner.ownerKey(SessionData("https://bookwyrm.social", "otro", "c")))
    }
}
