package app.cloudstream.desktop

import app.cloudstream.desktop.core.Accounts
import app.cloudstream.desktop.core.Tracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kitsu's JSON:API shapes, parsed without touching the network. */
class KitsuTest {

    @Test
    fun `a password grant response yields the tokens`() {
        val body = """
            {"access_token":"abc123","token_type":"bearer","expires_in":2592000,
             "refresh_token":"refresh456","scope":"public","created_at":1700000000}
        """.trimIndent()
        val token = Accounts.parseKitsuToken(body)
        assertEquals("abc123", token?.access)
        assertEquals("refresh456", token?.refresh)
        assertEquals(2592000, token?.expiresIn)
    }

    @Test
    fun `a failed login has no token`() {
        assertNull(Accounts.parseKitsuToken("""{"error":"invalid_grant","error_description":"Bad"}"""))
        assertNull(Accounts.parseKitsuToken("nonsense"))
    }

    @Test
    fun `the signed-in user is read from the self filter`() {
        val body = """{"data":[{"id":"42","type":"users","attributes":{"name":"max67"}}]}"""
        assertEquals("42" to "max67", Accounts.parseKitsuUser(body))
        assertNull(Accounts.parseKitsuUser("""{"data":[]}"""))
    }

    @Test
    fun `anime search returns the first media id`() {
        val body = """
            {"data":[{"id":"1234","type":"anime","attributes":{"canonicalTitle":"One Punch Man"}}],
             "meta":{"count":1}}
        """.trimIndent()
        assertEquals("1234", Tracking.parseKitsuAnimeId(body))
        assertNull(Tracking.parseKitsuAnimeId("""{"data":[]}"""))
    }

    @Test
    fun `an existing library entry is found so progress is patched not duplicated`() {
        val body = """{"data":[{"id":"987","type":"libraryEntries","attributes":{"progress":3}}]}"""
        assertEquals("987", Tracking.parseKitsuEntryId(body))
        assertNull(Tracking.parseKitsuEntryId("""{"data":[]}"""))
        assertNull(Tracking.parseKitsuEntryId("""{"errors":[{"title":"Unauthorized"}]}"""))
    }
}
