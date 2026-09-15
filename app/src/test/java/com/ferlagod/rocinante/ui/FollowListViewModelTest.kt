package com.ferlagod.rocinante.ui

import com.ferlagod.rocinante.data.model.BookWyrmProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class FollowListViewModelTest {

    private fun extractUsername(profile: BookWyrmProfile, actorUrl: String = ""): String {
        val preferred = profile.preferredUsername?.takeIf { it.isNotBlank() }
        if (preferred != null) {
            return preferred.removePrefix("@").substringBefore("@").trim()
        }
        val url = profile.id?.takeIf { it.isNotBlank() } ?: actorUrl
        val path = try { URI(url).path.orEmpty() } catch (_: Exception) { url }
        val cleanPath = path.removeSuffix("/").removeSuffix(".json")
        val segment = cleanPath.substringAfterLast("/user/").substringAfterLast("/users/").substringAfterLast("/").trim()
        if (segment.isNotBlank() && !segment.startsWith("http")) {
            return segment.removePrefix("@").substringBefore("@").trim()
        }
        val nameFallback = profile.name?.takeIf { it.isNotBlank() && !it.contains(" ") }
        if (nameFallback != null) {
            return nameFallback.removePrefix("@").substringBefore("@").trim()
        }
        return actorUrl.removeSuffix("/").substringAfterLast("/").removePrefix("@").substringBefore("@").trim()
    }

    private fun buildHandle(profile: BookWyrmProfile, myBaseUrl: String, actorUrl: String = ""): String {
        val username = extractUsername(profile, actorUrl)
        val myHost = try { URI(myBaseUrl).host ?: "" } catch (_: Exception) { "" }
        val actorHost = try {
            URI(profile.id ?: actorUrl).host ?: ""
        } catch (_: Exception) { "" }

        val domain = if (actorHost.isNotEmpty() && !actorHost.equals(myHost, ignoreCase = true)) {
            actorHost
        } else {
            val pref = profile.preferredUsername.orEmpty()
            if (pref.contains("@")) {
                val dom = pref.removePrefix("@").substringAfter("@", "").trim()
                if (dom.isNotEmpty() && !dom.equals(myHost, ignoreCase = true)) dom else null
            } else null
        }

        return if (!domain.isNullOrBlank()) {
            "@$username@$domain"
        } else {
            "@$username"
        }
    }

    private fun normalizeActorUrl(url: String): String {
        return url.removeSuffix(".json").removeSuffix("/").trim()
    }

    @Test
    fun testBuildHandleLocalUser() {
        val profile = BookWyrmProfile(
            id = "https://bookwyrm.it/user/alice",
            type = "Person",
            name = "Alice In Wonderland",
            summary = null,
            outbox = null,
            inbox = null,
            icon = null,
            preferredUsername = "alice",
            followers = null,
            following = null
        )
        val handle = buildHandle(profile, "https://bookwyrm.it/", "https://bookwyrm.it/user/alice")
        assertEquals("@alice", handle)
    }

    @Test
    fun testBuildHandleRemoteFederatedUser() {
        val profile = BookWyrmProfile(
            id = "https://mastodon.social/users/bob",
            type = "Person",
            name = "Bob The Builder",
            summary = null,
            outbox = null,
            inbox = null,
            icon = null,
            preferredUsername = "bob",
            followers = null,
            following = null
        )
        val handle = buildHandle(profile, "https://bookwyrm.it/", "https://mastodon.social/users/bob")
        assertEquals("@bob@mastodon.social", handle)
    }

    @Test
    fun testBuildHandleWithDomainInPreferredUsername() {
        val profile = BookWyrmProfile(
            id = "https://bookwyrm.it/user/bob@mastodon.social",
            type = "Person",
            name = "Bob The Builder",
            summary = null,
            outbox = null,
            inbox = null,
            icon = null,
            preferredUsername = "bob@mastodon.social",
            followers = null,
            following = null
        )
        val handle = buildHandle(profile, "https://bookwyrm.it/", "https://bookwyrm.it/user/bob@mastodon.social")
        assertEquals("@bob@mastodon.social", handle)
    }

    @Test
    fun testBuildHandleFallbackWithoutPreferredUsername() {
        val profile = BookWyrmProfile(
            id = "https://mastodon.social/users/charlie",
            type = "Person",
            name = "Charlie Brown",
            summary = null,
            outbox = null,
            inbox = null,
            icon = null,
            preferredUsername = null,
            followers = null,
            following = null
        )
        val handle = buildHandle(profile, "https://bookwyrm.it/", "https://mastodon.social/users/charlie")
        // No debe usar "Charlie Brown" como username
        assertEquals("@charlie@mastodon.social", handle)
    }

    @Test
    fun testFollowingMatchingLogic() {
        val myBaseUrl = "https://bookwyrm.it/"
        val myHost = URI(myBaseUrl).host?.lowercase()

        val myFollowingUrls = listOf(
            "https://bookwyrm.it/user/alice",
            "https://bookwyrm.it/user/bob@mastodon.social",
            "https://bookwyrm.social/user/kimeragupta"
        )

        val followingIds = mutableSetOf<String>()
        val followingHandles = mutableSetOf<String>()

        myFollowingUrls.forEach { url ->
            val norm = normalizeActorUrl(url)
            if (norm.isNotBlank()) {
                followingIds.add(norm)
                val host = try { URI(norm).host?.lowercase() } catch (_: Exception) { null }
                val rawSlug = norm.substringAfterLast("/user/").substringAfterLast("/users/").substringAfterLast("/").removePrefix("@").trim().lowercase()
                if (rawSlug.isNotBlank()) {
                    if (rawSlug.contains("@")) {
                        followingHandles.add(rawSlug)
                        followingHandles.add("@$rawSlug")
                    } else if (host != null && myHost != null && host.equals(myHost, ignoreCase = true)) {
                        followingHandles.add(rawSlug)
                        followingHandles.add("@$rawSlug")
                        followingHandles.add("$rawSlug@$host")
                        followingHandles.add("@$rawSlug@$host")
                    } else if (host != null) {
                        followingHandles.add("$rawSlug@$host")
                        followingHandles.add("@$rawSlug@$host")
                    }
                }
            }
        }

        fun checkIsFollowed(actorId: String, handle: String): Boolean {
            val cleanHandle = handle.removePrefix("@").trim().lowercase()
            val actorNorm = normalizeActorUrl(actorId)
            val actorHost = try { URI(actorNorm).host?.lowercase() } catch (_: Exception) { null }
            val actorSlug = actorNorm.substringAfterLast("/user/").substringAfterLast("/users/").substringAfterLast("/").removePrefix("@").trim().lowercase()
            val isLocalUser = (actorHost != null && myHost != null && actorHost.equals(myHost, ignoreCase = true)) || !cleanHandle.contains("@")

            return actorNorm in followingIds ||
                   cleanHandle in followingHandles ||
                   (actorHost != null && "$actorSlug@$actorHost" in followingHandles) ||
                   (isLocalUser && actorSlug in followingHandles)
        }

        // Caso 1: Alice (local en followers)
        assertTrue("Alice debe detectarse como seguida", checkIsFollowed("https://bookwyrm.it/user/alice", "@alice"))

        // Caso 2: Bob (remote actor URL en followers vs local federated URL en following)
        assertTrue("Bob debe detectarse como seguido por handle/slug cruzado", checkIsFollowed("https://mastodon.social/users/bob", "@bob@mastodon.social"))

        // Caso 3: Charlie (no seguido)
        assertFalse("Charlie no debe detectarse como seguido", checkIsFollowed("https://bookwyrm.it/user/charlie", "@charlie"))

        // Caso 4: KimeraGupta en comelibros.club NO debe detectarse como seguido aunque se siga a KimeraGupta en bookwyrm.social
        assertFalse("KimeraGupta de otra instancia no debe detectarse como seguido", checkIsFollowed("https://comelibros.club/user/kimeragupta", "@kimeragupta@comelibros.club"))

        // Caso 5: KimeraGupta en bookwyrm.social SÍ debe detectarse como seguido
        assertTrue("KimeraGupta de bookwyrm.social debe detectarse como seguido", checkIsFollowed("https://bookwyrm.social/user/kimeragupta", "@kimeragupta@bookwyrm.social"))
    }
}
