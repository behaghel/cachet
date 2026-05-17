package id.cachet.wallet.trusttrail.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GmailConfigTest {

    @Test
    fun `Gmail scope is gmail readonly`() {
        assertEquals(
            "https://www.googleapis.com/auth/gmail.readonly",
            GmailConfig.OAUTH_SCOPE,
        )
    }

    @Test
    fun `only one scope is requested`() {
        assertEquals(1, GmailConfig.allScopes.size)
    }

    @Test
    fun `default scan depth is 6 months`() {
        assertEquals(6, GmailConfig.DEFAULT_SCAN_DEPTH_MONTHS)
    }

    @Test
    fun `headers-only format is METADATA`() {
        assertEquals("METADATA", GmailConfig.HEADERS_ONLY_FORMAT)
    }

    @Test
    fun `requested metadata headers are From Subject Date`() {
        val headers = GmailConfig.METADATA_HEADERS
        assertTrue("From" in headers)
        assertTrue("Subject" in headers)
        assertTrue("Date" in headers)
        assertEquals(3, headers.size)
    }
}
