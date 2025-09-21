package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.model.ConsentReceipt
import id.cachet.wallet.domain.model.PresentationRequest
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.model.TransparencyLogEntry
import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Phase 2B transparency log integration
 */
class TransparencyLogTest {

    private val mockCredentialRepo = object : CredentialRepository {
        override suspend fun storeCredential(credential: StoredCredential) { /* no-op */ }
        override suspend fun getAllCredentials(): List<StoredCredential> = emptyList()
        override suspend fun getCredentialById(localId: String): StoredCredential? = null
        override suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential> = emptyList()
        override suspend fun markCredentialRevoked(localId: String) { /* no-op */ }
        override suspend fun deleteCredential(localId: String) { /* no-op */ }
    }

    private val consentReceiptRepo = InMemoryConsentReceiptRepository()
    private val transparencyLogRepo = MockTransparencyLogRepository()
    private val consentUseCase = ConsentUseCase(
        credentialRepository = mockCredentialRepo,
        consentReceiptRepository = consentReceiptRepo,
        transparencyLogRepository = transparencyLogRepo
    )

    @Test
    fun testTransparencyLogAnchoring() = runSuspendTest {
        val credential = VerifiableCredential(
            id = "test_cred_001",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "did:example:issuer",
            issuanceDate = Clock.System.now().toString(),
            credentialSubject = mapOf(
                "id" to JsonPrimitive("did:example:holder"),
                "personalData" to buildJsonObject { put("age", JsonPrimitive(25)) },
                "verified" to JsonPrimitive(true)
            )
        )

        val presentationRequest = PresentationRequest(
            rpIdentifier = "childcare.madrid.es",
            rpDisplayName = "Madrid Childcare Network",
            purpose = "Verify eligibility for childcare provider role",
            requestedPredicates = listOf("age_gte_18", "identity_verified")
        )

        val consentDetails = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true,
            retentionPeriodDays = 90,
            revocationRightsUnderstood = true
        )

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
        assertNotNull(logEntry.anchoredAt, "Should have anchoring timestamp")
    }

    @Test
    fun testTransparencyLogVerification() = runSuspendTest {
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
                sct = transparencyLogRepo.submitReceiptHash(
                    id.cachet.wallet.domain.model.AddEntryRequest(
                        receiptHash = "sha256:test_hash",
                        saltHash = "salt_hash",
                        policyId = "policy",
                        jurisdiction = "ES"
                    )
                ).getOrThrow().sct,
                anchoredAt = Clock.System.now(),
                isVerified = false
            )
        )

        consentReceiptRepo.storeReceipt(receipt).getOrThrow()

        val verificationResult = consentUseCase.verifyTransparencyLogInclusion(receipt)

        assertTrue(verificationResult.isSuccess, "Verification should succeed")
        assertTrue(verificationResult.getOrThrow(), "Mock verification should return true")
    }

    @Test
    fun testTransparencyLogAudit() = runSuspendTest {
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
        val testCases = mapOf(
            "childcare.madrid.es" to "ES",
            "verifier.paris.fr" to "FR",
            "trust.tallinn.ee" to "EE",
            "madrid.childcare.org" to "ES",
            "generic.com" to null
        )

        for ((rpId, expectedJurisdiction) in testCases) {
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

    private fun runSuspendTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }
}
