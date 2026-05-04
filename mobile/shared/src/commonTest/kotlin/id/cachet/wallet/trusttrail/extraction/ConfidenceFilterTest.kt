package id.cachet.wallet.trusttrail.extraction

import id.cachet.wallet.trusttrail.model.Claim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceFilterTest {

    @Test
    fun `claims below threshold are excluded`() {
        val claims = listOf(
            makeClaim("booking_confirmation", 0.9),
            makeClaim("date_reference", 0.4),
            makeClaim("payment_amount", 0.8),
            makeClaim("account_activity", 0.5),
        )

        val filtered = ClaimExtractor.filterByConfidence(claims, threshold = 0.7)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.confidence >= 0.7 })
        assertEquals("booking_confirmation", filtered[0].type)
        assertEquals("payment_amount", filtered[1].type)
    }

    @Test
    fun `claims at exactly threshold are included`() {
        val claims = listOf(
            makeClaim("repeat_client", 0.7),
        )

        val filtered = ClaimExtractor.filterByConfidence(claims, threshold = 0.7)

        assertEquals(1, filtered.size)
    }

    @Test
    fun `empty list returns empty`() {
        val filtered = ClaimExtractor.filterByConfidence(emptyList(), threshold = 0.7)

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `default threshold is 0_7`() {
        val claims = listOf(
            makeClaim("high", 0.9),
            makeClaim("low", 0.6),
        )

        val filtered = ClaimExtractor.filterByConfidence(claims)

        assertEquals(1, filtered.size)
        assertEquals("high", filtered[0].type)
    }

    private fun makeClaim(type: String, confidence: Double) = Claim(
        type = type,
        confidence = confidence,
        fields = emptyMap(),
        source = "test",
    )
}
