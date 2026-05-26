package com.ferlagod.rocinante

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testUrlParsing() {
        // BookWyrm status URLs
        val bwUrl1 = "https://bookwyrm.social/user/bob/status/12345"
        val bwUrl2 = "https://bookwyrm.social/user/bob/review/12345"
        val bwUrl3 = "https://example.com/user/alice/comment/999"
        
        // Mastodon status URLs
        val mastodonUrl = "https://mastodon.social/@charlie/1122334455"

        val urls = listOf(bwUrl1, bwUrl2, bwUrl3, mastodonUrl)
        val expectedUsernames = listOf("bob", "bob", "alice", "charlie")
        val expectedHosts = listOf("bookwyrm.social", "bookwyrm.social", "example.com", "mastodon.social")

        for (i in urls.indices) {
            val statusUrl = urls[i]
            val urlObj = java.net.URL(statusUrl)
            val host = urlObj.host
            val path = urlObj.path
            
            val username = when {
                path.startsWith("/user/") -> {
                    path.substringAfter("/user/").substringBefore("/")
                }
                path.contains("/@") -> {
                    path.substringAfter("/@").substringBefore("/")
                }
                else -> {
                    val segments = path.split("/").filter { it.isNotEmpty() }
                    if (segments.size >= 2) segments[1] else null
                }
            }

            assertEquals(expectedUsernames[i], username)
            assertEquals(expectedHosts[i], host)
        }
    }
}