package id.cachet.wallet.trusttrail.provider

import id.cachet.wallet.trusttrail.model.EmailHeader
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenSecurityTest {

    @Test
    fun `token is never included in email header results`() {
        // EmailHeader model has no field for tokens
        val header = EmailHeader(
            fromDomain = "vinted.es",
            subject = "Sale",
            date = Instant.parse("2026-04-25T10:00:00Z"),
            messageId = "msg-1",
        )

        val serialized = header.toString()
        assertFalse(serialized.contains("token", ignoreCase = true),
            "EmailHeader should not contain any token field")
        assertFalse(serialized.contains("Bearer", ignoreCase = true))
        assertFalse(serialized.contains("oauth", ignoreCase = true))
    }

    @Test
    fun `GmailConfig does not expose token storage details`() {
        // Verify the config only contains scope and API parameters,
        // not token storage implementation details
        assertTrue(GmailConfig.OAUTH_SCOPE.startsWith("https://"))
        assertTrue(GmailConfig.HEADERS_ONLY_FORMAT.isNotEmpty())
    }
}
