/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class HomeFeedPageTest {

    private fun fakeApi(code: Int, body: String, requested: MutableList<String>? = null): BookWyrmApi =
        Proxy.newProxyInstance(BookWyrmApi::class.java.classLoader, arrayOf(BookWyrmApi::class.java)) { _, method, args ->
            when (method.name) {
                "getRawHtmlResponse" -> {
                    requested?.add(args?.firstOrNull()?.toString().orEmpty())
                    if (code == 200) Response.success(body.toResponseBody())
                    else Response.error<okhttp3.ResponseBody>(code, "".toResponseBody())
                }
                else -> null
            }
        } as BookWyrmApi

    @Test
    fun `una pagina que no se puede leer es null para reintentarla`() = runBlocking {
        assertNull(BookWyrmScraper.scrapeHomeFeedPage(fakeApi(500, ""), "https://bookwyrm.es", 2))
        assertNull(BookWyrmScraper.scrapeHomeFeedPage(fakeApi(200, ""), "https://bookwyrm.es", 2))
    }

    @Test
    fun `una pagina leida sin actividades es una lista vacia`() = runBlocking {
        val items = BookWyrmScraper.scrapeHomeFeedPage(fakeApi(200, "<html><body></body></html>"), "https://bookwyrm.es", 3)
        assertNotNull(items)
        assertEquals(0, items!!.size)
    }

    @Test
    fun `pide la pagina indicada del feed`() = runBlocking {
        val requested = mutableListOf<String>()
        BookWyrmScraper.scrapeHomeFeedPage(fakeApi(200, "<html></html>", requested), "bookwyrm.es", 3)
        BookWyrmScraper.scrapeHomeFeedPage(fakeApi(200, "<html></html>", requested), "bookwyrm.es", 1)
        assertEquals(listOf("https://bookwyrm.es/?page=3", "https://bookwyrm.es/"), requested)
    }
}
