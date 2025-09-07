package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for Phase 2 consent receipt tamper-proofing features
 */
class ConsentReceiptCryptoTest {
    
    @Test
    fun testConsentReceiptHashGeneration() {
        // Create a sample consent receipt
        val receipt = ConsentReceipt(
            id = "test_receipt_001",
            timestamp = Clock.System.now(),
            purpose = "Verify identity for childcare services",
            predicatesProven = listOf("age_gte_18", "identity_verified", "liveness_verified"),
            rpIdentifier = "childcare.madrid.es",
            rpDisplayName = "Madrid Childcare Network",
            userConsent = ConsentDetails(
                explicitConsent = true,
                dataMinimizationAcknowledged = true,
                retentionPeriodUnderstood = true,
                retentionPeriodDays = 90,
                revocationRightsUnderstood = true
            ),
            credentialId = "cred_abc123"
        )
        
        // Test hash generation
        val salt1 = "test_salt_123"
        val hash1 = receipt.generateHash(salt1)
        val hash2 = receipt.generateHash(salt1) // Same salt should produce same hash
        val hash3 = receipt.generateHash("different_salt") // Different salt should produce different hash
        
        assertTrue(hash1.startsWith("sha256:"), "Hash should have sha256: prefix")
        assertTrue(hash1 == hash2, "Same salt should produce same hash")
        assertTrue(hash1 != hash3, "Different salt should produce different hash")
        assertTrue(hash1.length > 10, "Hash should be reasonably long")
    }
    
    @Test
    fun testCanonicalRepresentation() {
        val receipt = ConsentReceipt(
            id = "test_001",
            timestamp = Clock.System.now(),
            purpose = "Test purpose",
            predicatesProven = listOf("pred1", "pred2", "pred3"),
            rpIdentifier = "test.rp",
            rpDisplayName = "Test RP",
            userConsent = ConsentDetails(
                explicitConsent = true,
                dataMinimizationAcknowledged = false,
                retentionPeriodUnderstood = true,
                retentionPeriodDays = 30
            ),
            credentialId = "test_cred"
        )
        
        val canonical = receipt.buildCanonicalRepresentation()
        
        // Canonical representation should contain all important fields
        assertTrue(canonical.contains("id:test_001"))
        assertTrue(canonical.contains("purpose:Test purpose"))
        assertTrue(canonical.contains("predicates:pred1,pred2,pred3")) // Should be sorted
        assertTrue(canonical.contains("rp:test.rp"))
        assertTrue(canonical.contains("credential:test_cred"))
        assertTrue(canonical.contains("consent:"))
        assertTrue(canonical.contains("explicit:true"))
        assertTrue(canonical.contains("minimization:false"))
    }
    
    @Test
    fun testConsentDetailsCanonicalString() {
        val consent = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = false,
            retentionPeriodUnderstood = true,
            retentionPeriodDays = 45,
            revocationRightsUnderstood = false
        )
        
        val canonical = consent.toCanonicalString()
        
        assertTrue(canonical.contains("explicit:true"))
        assertTrue(canonical.contains("minimization:false"))
        assertTrue(canonical.contains("retention:true:45"))
        assertTrue(canonical.contains("revocation:false"))
    }
    
    @Test
    fun testSaltGeneration() {
        val salt1 = generateSalt()
        val salt2 = generateSalt()
        val salt3 = generateSalt(16)
        
        assertNotNull(salt1)
        assertNotNull(salt2)
        assertTrue(salt1 != salt2, "Different salt generations should be unique")
        assertTrue(salt1.length == 32, "Default salt should be 32 characters")
        assertTrue(salt3.length == 16, "Custom salt length should be respected")
        assertTrue(salt1.all { it.isLetterOrDigit() }, "Salt should contain only alphanumeric characters")
    }
}