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
        val myFollowingUrls = listOf(
            "https://bookwyrm.it/user/alice",
            "https://bookwyrm.it/user/bob@mastodon.social"
        )

        val followingIds = mutableSetOf<String>()
        val followingHandles = mutableSetOf<String>()

        myFollowingUrls.forEach { url ->
            val norm = normalizeActorUrl(url)
            if (norm.isNotBlank()) {
                followingIds.add(norm)
                val slug = norm.substringAfterLast("/user/").substringAfterLast("/users/").substringAfterLast("/").removePrefix("@").trim().lowercase()
                if (slug.isNotBlank()) {
                    followingHandles.add(slug)
                    followingHandles.add("@$slug")
                }
            }
        }

        // Caso 1: Alice (local en followers)
        val aliceActorId = "https://bookwyrm.it/user/alice"
        val aliceHandle = "@alice"
        val aliceNorm = normalizeActorUrl(aliceActorId)
        val aliceSlug = aliceNorm.substringAfterLast("/user/").removePrefix("@").lowercase()
        val aliceFollowed = aliceNorm in followingIds || aliceHandle.removePrefix("@").lowercase() in followingHandles || aliceSlug in followingHandles
        assertTrue("Alice debe detectarse como seguida", aliceFollowed)

        // Caso 2: Bob (remote actor URL en followers vs local federated URL en following)
        val bobActorId = "https://mastodon.social/users/bob"
        val bobHandle = "@bob@mastodon.social"
        val bobNorm = normalizeActorUrl(bobActorId)
        val bobCleanHandle = bobHandle.removePrefix("@").lowercase()
        val bobSlug = bobNorm.substringAfterLast("/users/").removePrefix("@").lowercase()
        val bobFollowed = bobNorm in followingIds || bobCleanHandle in followingHandles || bobSlug in followingHandles
        assertTrue("Bob debe detectarse como seguido por handle/slug cruzado", bobFollowed)

        // Caso 3: Charlie (no seguido)
        val charlieActorId = "https://bookwyrm.it/user/charlie"
        val charlieHandle = "@charlie"
        val charlieNorm = normalizeActorUrl(charlieActorId)
        val charlieCleanHandle = charlieHandle.removePrefix("@").lowercase()
        val charlieSlug = charlieNorm.substringAfterLast("/user/").removePrefix("@").lowercase()
        val charlieFollowed = charlieNorm in followingIds || charlieCleanHandle in followingHandles || charlieSlug in followingHandles
        assertFalse("Charlie no debe detectarse como seguido", charlieFollowed)
    }
}
