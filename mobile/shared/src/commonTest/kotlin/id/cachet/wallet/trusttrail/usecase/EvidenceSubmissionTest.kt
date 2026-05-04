package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.Claim
import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.model.EvidenceBundle
import id.cachet.wallet.trusttrail.model.TrustLevel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvidenceSubmissionTest {

    private val testDate = Instant.parse("2026-04-25T10:00:00Z")

    // --- Claim deselection ---

    @Test
    fun `deselected claims absent from bundle`() {
        val claims = listOf(
            makeClaim("sale_notification", 0.95),
            makeClaim("sale_amount", 0.9),
            makeClaim("buyer_identity", 0.85),
        )
        val evidence = makeEvidence("vinted", claims)

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(evidence),
            deselectedClaimIds = setOf("vinted:sale_amount:1"),
        )

        assertEquals(2, bundle.claims.size)
        assertTrue(bundle.claims.none { it.type == "sale_amount" })
        assertTrue(bundle.claims.any { it.type == "sale_notification" })
        assertTrue(bundle.claims.any { it.type == "buyer_identity" })
    }

    @Test
    fun `all claims included when none deselected`() {
        val claims = listOf(
            makeClaim("sale_notification", 0.95),
            makeClaim("sale_amount", 0.9),
        )
        val evidence = makeEvidence("vinted", claims)

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(evidence),
            deselectedClaimIds = emptySet(),
        )

        assertEquals(2, bundle.claims.size)
    }

    // --- Payload shape ---

    @Test
    fun `bundle claim includes type, fields, confidence, trust_level, platform, date`() {
        val claim = Claim("sale_amount", 0.9, mapOf("amount" to "40,00"), "body_html")
        val evidence = EmailEvidence(
            platform = "vinted",
            fromDomain = "vinted.es",
            subject = "Sale",
            receivedDate = testDate,
            claims = listOf(claim),
            trustLevel = TrustLevel.CRYPTOGRAPHIC,
        )

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(evidence),
            deselectedClaimIds = emptySet(),
        )

        val bundleClaim = bundle.claims[0]
        assertEquals("sale_amount", bundleClaim.type)
        assertEquals(mapOf("amount" to "40,00"), bundleClaim.fields)
        assertEquals(0.9, bundleClaim.confidence, 0.01)
        assertEquals(TrustLevel.CRYPTOGRAPHIC, bundleClaim.trustLevel)
        assertEquals("vinted", bundleClaim.platform)
        assertEquals(testDate, bundleClaim.date)
    }

    @Test
    fun `bundle does not contain raw email content`() {
        val evidence = makeEvidence("vinted", listOf(makeClaim("sale", 0.95)))

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(evidence),
            deselectedClaimIds = emptySet(),
        )

        // EvidenceBundle has no field for raw email content
        val serialized = bundle.toString()
        assertFalse(serialized.contains("rawMime", ignoreCase = true))
        assertFalse(serialized.contains("textBody", ignoreCase = true))
        assertFalse(serialized.contains("htmlBody", ignoreCase = true))
    }

    @Test
    fun `bundle includes DKIM verification proof`() {
        val evidence = EmailEvidence(
            platform = "vinted",
            fromDomain = "vinted.es",
            subject = "Sale",
            receivedDate = testDate,
            claims = listOf(makeClaim("sale", 0.95)),
            trustLevel = TrustLevel.CRYPTOGRAPHIC,
            dkimDomain = "vinted.es",
        )

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(evidence),
            deselectedClaimIds = emptySet(),
        )

        val proof = bundle.claims[0]
        assertEquals(TrustLevel.CRYPTOGRAPHIC, proof.trustLevel)
        assertEquals("vinted.es", proof.dkimDomain)
    }

    // --- Foundational identity gate ---

    @Test
    fun `submission blocked without foundational identity`() = runTest {
        val submitter = EvidenceSubmissionUseCase(
            hasFoundationalIdentity = { false },
            submitBundle = { error("should not be called") },
        )

        val result = submitter.submit(EvidenceBundle(emptyList()))

        assertFalse(result.success)
        assertEquals("foundational_identity_required", result.reason)
    }

    @Test
    fun `submission allowed with foundational identity`() = runTest {
        var submittedBundle: EvidenceBundle? = null
        val submitter = EvidenceSubmissionUseCase(
            hasFoundationalIdentity = { true },
            submitBundle = { bundle -> submittedBundle = bundle; true },
        )

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(makeEvidence("vinted", listOf(makeClaim("sale", 0.95)))),
            deselectedClaimIds = emptySet(),
        )

        val result = submitter.submit(bundle)

        assertTrue(result.success)
        assertNull(result.reason)
        assertEquals(bundle, submittedBundle)
    }

    @Test
    fun `rejected evidence excluded from bundle`() {
        val good = makeEvidence("vinted", listOf(makeClaim("sale", 0.95)))
        val rejected = EmailEvidence(
            platform = "vinted",
            fromDomain = "vinted.es",
            subject = "Fwd: Sale",
            receivedDate = testDate,
            claims = emptyList(),
            rejected = true,
            rejectionReason = "forwarded_email",
        )

        val bundle = EvidenceBundleBuilder.build(
            evidence = listOf(good, rejected),
            deselectedClaimIds = emptySet(),
        )

        assertEquals(1, bundle.claims.size)
    }

    // --- Helpers ---

    private fun makeEvidence(platform: String, claims: List<Claim>) = EmailEvidence(
        platform = platform,
        fromDomain = "$platform.test",
        subject = "Test",
        receivedDate = testDate,
        claims = claims,
        trustLevel = TrustLevel.MTA_ATTESTED,
    )

    private fun makeClaim(type: String, confidence: Double) = Claim(
        type = type,
        confidence = confidence,
        fields = emptyMap(),
        source = "test",
    )
}
