/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.repository

import com.ferlagod.rocinante.data.api.BookWyrmApi
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class FollowActionsTest {

    private val base = "https://bookwyrm.es/"

    /** API falsa: apunta cada llamada y acepta solo los valores de [accepted]. */
    private class FakeApi(val accepted: Set<String>, val acceptAfterSearch: Set<String> = emptySet()) {
        val calls = mutableListOf<String>()
        private var searched = mutableSetOf<String>()
        val api: BookWyrmApi = Proxy.newProxyInstance(
            BookWyrmApi::class.java.classLoader, arrayOf(BookWyrmApi::class.java)
        ) { _, method, args ->
            val arg = args?.firstOrNull()?.toString().orEmpty()
            calls += "${method.name}:$arg"
            when (method.name) {
                "getRawHtmlResponse" -> {
                    java.net.URLDecoder.decode(arg.substringAfter("q=").substringBefore("&"), "UTF-8").let { searched += it }
                    Response.success("".toResponseBody())
                }
                "followUser", "unfollowUser", "acceptFollowRequest", "deleteFollowRequest" ->
                    if (arg in accepted || (arg in acceptAfterSearch && arg in searched)) Response.success("".toResponseBody())
                    else Response.error(404, "".toResponseBody())
                else -> null
            }
        } as BookWyrmApi
    }

    @Test
    fun `nunca prueba el nombre sin servidor de un usuario remoto`() {
        val candidates = FollowActions.followCandidates(base, "@ana@otro.club", "https://otro.club/user/ana")
        assertFalse("ana" in candidates)
        assertEquals("ana@otro.club", candidates.first())
    }

    @Test
    fun `un usuario local se prueba tambien por su nombre`() {
        val candidates = FollowActions.followCandidates(base, "", "https://bookwyrm.es/user/luis")
        assertEquals(listOf("luis", "https://bookwyrm.es/user/luis"), candidates)
    }

    @Test
    fun `un actor de mastodon se nombra con su servidor`() {
        val candidates = FollowActions.followCandidates(base, "", "https://mastodon.social/@bob")
        assertEquals("bob@mastodon.social", candidates.first())
        assertFalse("bob" in candidates)
    }

    @Test
    fun `para en el primer intento aceptado`() = runBlocking {
        val fake = FakeApi(accepted = setOf("ana@otro.club"))
        val result = FollowActions.follow(fake.api, base, "@ana@otro.club", "https://otro.club/user/ana")
        assertTrue(result.success)
        assertEquals(listOf("followUser:ana@otro.club"), fake.calls)
    }

    @Test
    fun `descubre al remoto desconocido y reintenta`() = runBlocking {
        val fake = FakeApi(accepted = emptySet(), acceptAfterSearch = setOf("ana@otro.club"))
        val result = FollowActions.follow(fake.api, base, "@ana@otro.club", "https://otro.club/user/ana")
        assertTrue(result.success)
        assertEquals(
            listOf(
                "followUser:ana@otro.club",
                "getRawHtmlResponse:https://bookwyrm.es/search?q=ana%40otro.club&type=user",
                "followUser:ana@otro.club"
            ),
            fake.calls
        )
    }

    @Test
    fun `dejar de seguir no busca usuarios`() = runBlocking {
        val fake = FakeApi(accepted = emptySet())
        val result = FollowActions.unfollow(fake.api, base, "@ana@otro.club", "https://otro.club/user/ana")
        assertFalse(result.success)
        assertEquals(404, result.code)
        assertTrue(fake.calls.none { it.startsWith("getRawHtmlResponse") })
        assertTrue(fake.calls.none { it == "unfollowUser:ana" })
    }

    @Test
    fun `aceptar prueba primero el id del formulario`() = runBlocking {
        val fake = FakeApi(accepted = setOf("ana@otro.club"))
        val result = FollowActions.acceptRequest(fake.api, "ana@otro.club", "@ana@otro.club", "https://otro.club/user/ana")
        assertTrue(result.success)
        assertEquals(listOf("acceptFollowRequest:ana@otro.club"), fake.calls)
    }
}
