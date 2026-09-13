/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.model

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileIconDeserializerTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ProfileIcon::class.java, ProfileIconDeserializer())
        .create()

    @Test
    fun testDeserializeIconAsObject() {
        val json = """
            {
                "id": "https://bookwyrm.it/user/testuser",
                "name": "Test User",
                "icon": {
                    "type": "Image",
                    "url": "https://bookwyrm.it/images/avatars/avatar.jpg"
                }
            }
        """.trimIndent()

        val profile = gson.fromJson(json, BookWyrmProfile::class.java)
        assertNotNull(profile)
        assertEquals("https://bookwyrm.it/images/avatars/avatar.jpg", profile.icon?.url)
    }

    @Test
    fun testDeserializeIconAsStringUrl() {
        val json = """
            {
                "id": "https://mastodon.social/users/mastouser",
                "name": "Masto User",
                "icon": "https://mastodon.social/system/accounts/avatars/avatar.png"
            }
        """.trimIndent()

        val profile = gson.fromJson(json, BookWyrmProfile::class.java)
        assertNotNull(profile)
        assertEquals("https://mastodon.social/system/accounts/avatars/avatar.png", profile.icon?.url)
    }

    @Test
    fun testDeserializeIconAsArray() {
        val json = """
            {
                "id": "https://pleroma.site/users/pleromauser",
                "name": "Pleroma User",
                "icon": [
                    {
                        "type": "Image",
                        "url": "https://pleroma.site/media/avatar.png"
                    }
                ]
            }
        """.trimIndent()

        val profile = gson.fromJson(json, BookWyrmProfile::class.java)
        assertNotNull(profile)
        assertEquals("https://pleroma.site/media/avatar.png", profile.icon?.url)
    }

    @Test
    fun testDeserializeIconNullOrMissing() {
        val json = """
            {
                "id": "https://bookwyrm.it/user/noavatar",
                "name": "No Avatar"
            }
        """.trimIndent()

        val profile = gson.fromJson(json, BookWyrmProfile::class.java)
        assertNotNull(profile)
        assertNull(profile.icon)
    }
}
