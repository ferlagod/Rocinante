package com.ferlagod.rocinante.utils

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteSuccessTest {

    private fun response(code: Int, location: String? = null): retrofit2.Response<okhttp3.ResponseBody> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://bookwyrm.it/shelve/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .apply { if (location != null) header("Location", location) }
            .build()
        return if (code in 200..299) retrofit2.Response.success("".toResponseBody(null), raw)
        else retrofit2.Response.error("".toResponseBody(null), raw)
    }

    @Test
    fun `una redireccion normal tras guardar es exito`() {
        assertTrue(BookWyrmUtils.isWriteSuccess(response(302, "/book/12")))
        assertTrue(BookWyrmUtils.isWriteSuccess(response(200)))
    }

    @Test
    fun `la redireccion al login o al reto de anubis no es exito`() {
        assertFalse(BookWyrmUtils.isWriteSuccess(response(302, "/login?next=/shelve/")))
        assertFalse(BookWyrmUtils.isWriteSuccess(response(307, "/.within.website/?redir=/shelve/")))
        assertFalse(BookWyrmUtils.isWriteSuccess(response(403)))
    }
}
