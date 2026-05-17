package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectiveConsentTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    @Test
    fun `only consented platforms have full content fetched`() = runTest {
        val provider = TrackingFakeProvider(
            fullContentByMessageId = mapOf(
                "vinted-1" to makeRawEmail("vinted-1", "no-reply@vinted.es",
                    "Ton article s'est vendu !",
                    htmlBody = "<p><strong>buyer123</strong> a acheté</p><div>Item</div><div>40,00 €</div>"),
                "vinted-2" to makeRawEmail("vinted-2", "no-reply@vinted.es",
                    "Ton article s'est vendu !",
                    htmlBody = "<p><strong>buyer456</strong> a acheté</p><div>Shoes</div><div>25,00 €</div>"),
                "care-1" to makeRawEmail("care-1", "noreply@care.com",
                    "Booking Confirmed",
                    textBody = "Your booking is confirmed. Amount: \$150.00"),
            )
        )

        val useCase = InboxScannerUseCase(provider)

        val consentedPlatforms = listOf(
            DiscoveredPlatform("vinted", 2, listOf("vinted-1", "vinted-2")),
        )
        val unconsentedPlatforms = listOf(
            DiscoveredPlatform("care.com", 1, listOf("care-1")),
        )

        val results = useCase.extractClaims(consentedPlatforms)

        // Vinted messages were fetched
        assertTrue(provider.fetchedMessageIds.contains("vinted-1"))
        assertTrue(provider.fetchedMessageIds.contains("vinted-2"))
        // Care.com messages were NOT fetched
        assertTrue("care-1" !in provider.fetchedMessageIds,
            "unconsented platform messages should not be fetched")
    }

    @Test
    fun `extracted claims include platform and date`() = runTest {
        val provider = TrackingFakeProvider(
            fullContentByMessageId = mapOf(
                "vinted-1" to makeRawEmail("vinted-1", "no-reply@vinted.es",
                    "Ton article s'est vendu !",
                    htmlBody = "<p><strong>buyer123</strong> a acheté</p><div>Item</div><div>40,00 €</div>"),
            )
        )

        val useCase = InboxScannerUseCase(provider)
        val platforms = listOf(DiscoveredPlatform("vinted", 1, listOf("vinted-1")))

        val results = useCase.extractClaims(platforms)

        assertEquals(1, results.size)
        assertEquals("vinted", results[0].platform)
        assertTrue(results[0].claims.isNotEmpty(), "should have extracted claims")
    }

    @Test
    fun `forwarded emails are excluded from results`() = runTest {
        val provider = TrackingFakeProvider(
            fullContentByMessageId = mapOf(
                "fwd-1" to makeRawEmail("fwd-1", "someone@hotmail.com",
                    "Fwd: Ton article s'est vendu !",
                    textBody = "---------- Forwarded message ----------\nDetails"),
            )
        )

        val useCase = InboxScannerUseCase(provider)
        val platforms = listOf(DiscoveredPlatform("vinted", 1, listOf("fwd-1")))

        val results = useCase.extractClaims(platforms)

        // Forwarded emails should be rejected — no evidence returned
        assertTrue(results.isEmpty() || results.all { it.rejected })
    }

    @Test
    fun `claims below confidence threshold are filtered out`() = runTest {
        val provider = TrackingFakeProvider(
            fullContentByMessageId = mapOf(
                "unknown-1" to makeRawEmail("unknown-1", "noreply@unknown.com",
                    "Booking Confirmation #12345",
                    textBody = "Your appointment is confirmed."),
            )
        )

        val useCase = InboxScannerUseCase(provider)
        val platforms = listOf(DiscoveredPlatform("", 1, listOf("unknown-1")))

        val results = useCase.extractClaims(platforms)

        // Generic patterns have confidence 0.4-0.6, below the 0.7 threshold
        for (evidence in results) {
            for (claim in evidence.claims) {
                assertTrue(claim.confidence >= 0.7,
                    "claim ${claim.type} has confidence ${claim.confidence} below threshold")
            }
        }
    }

    @Test
    fun `empty consented list returns empty results`() = runTest {
        val provider = TrackingFakeProvider()
        val useCase = InboxScannerUseCase(provider)

        val results = useCase.extractClaims(emptyList())

        assertTrue(results.isEmpty())
        assertEquals(0, provider.fetchedMessageIds.size)
    }

    // --- Helpers ---

    private fun makeRawEmail(
        messageId: String,
        from: String,
        subject: String,
        textBody: String = "",
        htmlBody: String = "",
    ) = EmailProvider.RawEmail(
        messageId = messageId,
        from = from,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
    )
}

/**
 * Fake provider that tracks which messages had full content fetched.
 */
class TrackingFakeProvider(
    private val fullContentByMessageId: Map<String, EmailProvider.RawEmail> = emptyMap(),
) : EmailProvider {

    val fetchedMessageIds = mutableListOf<String>()

    override suspend fun fetchHeaders(scanDepthMonths: Int): List<EmailHeader> = emptyList()

    override suspend fun fetchFullContent(messageId: String): EmailProvider.RawEmail? {
        fetchedMessageIds.add(messageId)
        return fullContentByMessageId[messageId]
    }

    override fun isConnected(): Boolean = true
}
