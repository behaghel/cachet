package id.cachet.wallet.domain.model

import id.cachet.wallet.testfixtures.makeCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialQualityTest {

    // ── VerificationLevel.fromString ──

    @Test
    fun `fromString maps basic`() {
        assertEquals(VerificationLevel.BASIC, VerificationLevel.fromString("basic"))
    }

    @Test
    fun `fromString maps premium case-insensitive`() {
        assertEquals(VerificationLevel.PREMIUM, VerificationLevel.fromString("Premium"))
        assertEquals(VerificationLevel.PREMIUM, VerificationLevel.fromString("PREMIUM"))
    }

    @Test
    fun `fromString maps gold`() {
        assertEquals(VerificationLevel.GOLD, VerificationLevel.fromString("gold"))
    }

    @Test
    fun `fromString returns BASIC for null`() {
        assertEquals(VerificationLevel.BASIC, VerificationLevel.fromString(null))
    }

    @Test
    fun `fromString returns BASIC for unknown string`() {
        assertEquals(VerificationLevel.BASIC, VerificationLevel.fromString("unknown"))
    }

    // ── extractQuality ──

    @Test
    fun `extractQuality uses verificationLevel from subject`() {
        val cred = makeCredential(verificationLevel = "gold")
        val quality = cred.extractQuality()
        assertNotNull(quality)
        assertEquals(VerificationLevel.GOLD, quality.verificationLevel)
    }

    @Test
    fun `extractQuality uses metrics when present`() {
        val cred = makeCredential(
            verificationMetrics = VerificationMetrics(
                overallConfidence = 0.95,
                riskScore = 0.05,
                livenessScore = 0.99,
                documentAuthenticity = 0.98
            )
        )
        val quality = cred.extractQuality()
        assertNotNull(quality)
        assertEquals(0.95, quality.overallConfidence)
        assertEquals(0.05, quality.riskScore)
    }

    @Test
    fun `extractQuality returns defaults when metrics absent`() {
        val cred = makeCredential(verificationMetrics = null)
        val quality = cred.extractQuality()
        assertNotNull(quality)
        assertEquals(0.85, quality.overallConfidence)
        assertEquals(0.0, quality.riskScore)
    }

    // ── getQualityBadge ──

    @Test
    fun `getQualityBadge includes emoji and display name`() {
        val cred = makeCredential(verificationLevel = "premium")
        val badge = cred.getQualityBadge()
        assertTrue(badge.contains(VerificationLevel.PREMIUM.emoji))
        assertTrue(badge.contains(VerificationLevel.PREMIUM.displayName))
    }

    // ── meetsQualityThreshold ──

    @Test
    fun `meetsQualityThreshold returns true for high-quality credential`() {
        val cred = makeCredential(
            issuanceDate = "2026-04-01T10:00:00Z",
            verificationMetrics = VerificationMetrics(
                overallConfidence = 0.95,
                riskScore = 0.05
            )
        )
        assertTrue(cred.meetsQualityThreshold())
    }
}
