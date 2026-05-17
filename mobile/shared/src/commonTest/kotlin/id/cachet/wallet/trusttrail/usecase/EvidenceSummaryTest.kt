package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.Claim
import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.model.PlatformSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvidenceSummaryTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    @Test
    fun `evidence grouped by platform`() {
        val evidenceList = listOf(
            makeEvidence("vinted", listOf(
                makeClaim("sale_notification", 0.95),
                makeClaim("sale_amount", 0.9),
            )),
            makeEvidence("vinted", listOf(
                makeClaim("sale_notification", 0.95),
            )),
            makeEvidence("care.com", listOf(
                makeClaim("booking_confirmation", 0.9),
            )),
        )

        val summary = EvidenceSummarizer.groupByPlatform(evidenceList)

        assertEquals(2, summary.size)

        val vinted = summary.first { it.platform == "vinted" }
        assertEquals(3, vinted.totalClaims)
        assertEquals(2, vinted.emailCount)

        val care = summary.first { it.platform == "care.com" }
        assertEquals(1, care.totalClaims)
        assertEquals(1, care.emailCount)
    }

    @Test
    fun `rejected evidence excluded from summary`() {
        val evidenceList = listOf(
            makeEvidence("vinted", listOf(makeClaim("sale_notification", 0.95))),
            EmailEvidence(
                platform = "vinted",
                fromDomain = "vinted.es",
                subject = "Fwd: Sale",
                receivedDate = testDate,
                claims = emptyList(),
                rejected = true,
                rejectionReason = "forwarded_email",
            ),
        )

        val summary = EvidenceSummarizer.groupByPlatform(evidenceList)

        assertEquals(1, summary.size)
        assertEquals(1, summary[0].emailCount)
    }

    @Test
    fun `summary sorted by total claims descending`() {
        val evidenceList = listOf(
            makeEvidence("care.com", listOf(makeClaim("booking", 0.9))),
            makeEvidence("vinted", listOf(
                makeClaim("sale", 0.95),
                makeClaim("amount", 0.9),
                makeClaim("buyer", 0.85),
            )),
            makeEvidence("homeexchange.com", listOf(
                makeClaim("exchange", 0.9),
                makeClaim("dates", 0.9),
            )),
        )

        val summary = EvidenceSummarizer.groupByPlatform(evidenceList)

        assertEquals("vinted", summary[0].platform)
        assertEquals("homeexchange.com", summary[1].platform)
        assertEquals("care.com", summary[2].platform)
    }

    @Test
    fun `empty evidence list returns empty summary`() {
        val summary = EvidenceSummarizer.groupByPlatform(emptyList())

        assertTrue(summary.isEmpty())
    }

    @Test
    fun `each claim in summary includes type date confidence`() {
        val evidenceList = listOf(
            makeEvidence("vinted", listOf(
                Claim("sale_notification", 0.95, mapOf("matched" to "vendu"), "subject"),
                Claim("sale_amount", 0.9, mapOf("amount" to "40,00"), "body_html"),
            )),
        )

        val summary = EvidenceSummarizer.groupByPlatform(evidenceList)
        val claims = summary[0].claims

        assertEquals(2, claims.size)
        assertEquals("sale_notification", claims[0].type)
        assertEquals(0.95, claims[0].confidence, 0.01)
        assertEquals("sale_amount", claims[1].type)
        assertEquals("40,00", claims[1].fields["amount"])
    }

    // --- Helpers ---

    private fun makeEvidence(platform: String, claims: List<Claim>) = EmailEvidence(
        platform = platform,
        fromDomain = "$platform.test",
        subject = "Test email",
        receivedDate = testDate,
        claims = claims,
    )

    private fun makeClaim(type: String, confidence: Double) = Claim(
        type = type,
        confidence = confidence,
        fields = emptyMap(),
        source = "test",
    )
}
