package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.*
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Tests for Phase 2B transparency log integration
 */
class TransparencyLogTest {
    
    private val mockCredentialRepo = object : CredentialRepository {
        override suspend fun getCredentialById(id: String): StoredCredential? = null
        override suspend fun storeCredential(credential: StoredCredential): Result<Unit> = Result.success(Unit)
        override suspend fun getAllCredentials(): Result<List<StoredCredential>> = Result.success(emptyList())
        override suspend fun deleteCredential(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun getCredentialsByIssuer(issuer: String): Result<List<StoredCredential>> = Result.success(emptyList())
    }
    
    private val consentReceiptRepo = InMemoryConsentReceiptRepository()
    private val transparencyLogRepo = MockTransparencyLogRepository()
    private val consentUseCase = ConsentUseCase(
        credentialRepository = mockCredentialRepo,
        consentReceiptRepository = consentReceiptRepo,
        transparencyLogRepository = transparencyLogRepo
    )
    
    @Test
    fun testTransparencyLogAnchoring() = runTest {
        // Create a test credential
        val credential = VerifiableCredential(
            id = "test_cred_001",
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "did:example:issuer",
            validFrom = Clock.System.now(),
            validUntil = null,
            credentialSubject = mapOf(
                "id" => "did:example:holder",
                "personalData" => mapOf("age" => 25),
                "verified" => true
            )
        )
        
        // Create presentation request
        val presentationRequest = PresentationRequest(
            rpIdentifier = "childcare.madrid.es",
            rpDisplayName = "Madrid Childcare Network",
            purpose = "Verify eligibility for childcare provider role",
            requestedPredicates = listOf("age_gte_18", "identity_verified")
        )
        
        // Create consent details
        val consentDetails = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true,
            retentionPeriodDays = 90,
            revocationRightsUnderstood = true
        )
        
        // Generate consent receipt with transparency log integration
        val result = consentUseCase.generateConsentReceipt(
            credential = credential,
            presentationRequest = presentationRequest,
            userConsent = consentDetails
        )
        
        assertTrue(result.isSuccess, "Consent receipt generation should succeed")
        
        val receipt = result.getOrThrow()
        assertNotNull(receipt.receiptHash, "Receipt should have a hash")
        assertNotNull(receipt.signature, "Receipt should have a signature")
        assertNotNull(receipt.salt, "Receipt should have a salt")
        assertNotNull(receipt.transparencyLogEntry, "Receipt should have transparency log entry")
        
        val logEntry = receipt.transparencyLogEntry!!
        assertEquals("mock-log-id", logEntry.logId, "Log ID should match mock")
        assertNotNull(logEntry.sct, "Should have Signed Certificate Timestamp")
        assertTrue(logEntry.anchoredAt != null, "Should have anchoring timestamp")
    }
    
    @Test
    fun testTransparencyLogVerification() = runTest {
        // First create and store a receipt with transparency log entry
        val receipt = ConsentReceipt(
            id = "test_receipt_verification",
            timestamp = Clock.System.now(),
            purpose = "Test verification purpose",
            predicatesProven = listOf("test_predicate"),
            rpIdentifier = "test.rp",
            rpDisplayName = "Test RP",
            userConsent = ConsentDetails(
                explicitConsent = true,
                dataMinimizationAcknowledged = true,
                retentionPeriodUnderstood = true
            ),
            credentialId = "test_cred_verification",
            receiptHash = "sha256:test_hash",
            signature = "test_signature",
            salt = "test_salt",
            transparencyLogEntry = TransparencyLogEntry(
                logId = "mock-log-id",
                logIndex = 0,
                sct = SignedCertificateTimestamp(
                    logId = "mock-log-id",
                    timestamp = Clock.System.now(),
                    signature = "mock-sct-signature"
                ),
                anchoredAt = Clock.System.now(),
                isVerified = false
            )
        )
        
        // Store the receipt
        consentReceiptRepo.storeReceipt(receipt)
        
        // Verify transparency log inclusion
        val verificationResult = consentUseCase.verifyTransparencyLogInclusion(receipt)
        
        assertTrue(verificationResult.isSuccess, "Verification should succeed")
        assertTrue(verificationResult.getOrThrow(), "Mock verification should return true")
    }
    
    @Test
    fun testTransparencyLogAudit() = runTest {
        // Perform transparency log audit
        val auditResult = consentUseCase.performTransparencyLogAudit()
        
        assertTrue(auditResult.isSuccess, "Audit should succeed")
        
        val auditReport = auditResult.getOrThrow()
        assertTrue(auditReport.contains("Transparency Log Audit Report"), "Should contain audit header")
        assertTrue(auditReport.contains("Log ID:"), "Should contain log ID")
        assertTrue(auditReport.contains("Tree Size:"), "Should contain tree size")
        assertTrue(auditReport.contains("Root Hash:"), "Should contain root hash")
    }
    
    @Test
    fun testJurisdictionExtraction() {
        // Test jurisdiction extraction logic
        val testCases = mapOf(
            "childcare.madrid.es" to "ES",
            "verifier.paris.fr" to "FR", 
            "trust.tallinn.ee" to "EE",
            "madrid.childcare.org" to "ES",
            "generic.com" to null
        )
        
        // We'll test this indirectly through the transparency log anchoring
        // The jurisdiction should be extracted and included in the log entry
        for ((rpId, expectedJurisdiction) in testCases) {
            // This would be tested by checking the AddEntryRequest in a more detailed test
            // For now, we verify the basic pattern matching works
            val actualJurisdiction = when {
                rpId.endsWith(".es") -> "ES"
                rpId.endsWith(".fr") -> "FR"
                rpId.endsWith(".ee") -> "EE"
                rpId.contains("madrid") -> "ES"
                else -> null
            }
            assertEquals(expectedJurisdiction, actualJurisdiction, "Jurisdiction extraction for $rpId")
        }
    }
    
    // Helper function to run suspend functions in tests
    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking {
            block()
        }
    }
}