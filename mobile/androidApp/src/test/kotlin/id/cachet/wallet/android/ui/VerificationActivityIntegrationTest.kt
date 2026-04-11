package id.cachet.wallet.android.ui

import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.mapper.ActivityMapper
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Integration test: verification → consent receipt → activity entry.
 *
 * Exercises the full chain from [VerificationUseCase.verifyCredential]
 * through [ConsentUseCase] storage to [ActivityMapper.toHistoryEntry] and
 * asserts the activity entry reflects the actual verification outcome.
 */
class VerificationActivityIntegrationTest {

    private val credRepo = TestCredentialRepository()
    private val verifierClient = TestVerifierClient()
    private val relayClient = TestRelayClient()
    private val receiptRepo = InMemoryConsentReceiptRepository()
    private val consentUseCase = ConsentUseCase(
        credRepo,
        receiptRepo,
        MockTransparencyLogRepository()
    )
    private val useCase = VerificationUseCase(
        credentialRepository = credRepo,
        verifierClient = verifierClient,
        relayClient = relayClient,
        consentUseCase = consentUseCase
    )

    private fun storeCredential(localId: String) {
        credRepo.creds.add(
            StoredCredential(
                localId = localId,
                credential = VerifiableCredential(
                    id = localId,
                    context = listOf("https://www.w3.org/2018/credentials/v1"),
                    type = listOf("VerifiableCredential", "IdentityCredential"),
                    issuer = "did:web:issuer.cachet.id",
                    issuanceDate = "2026-01-15T10:00:00Z",
                    credentialSubject = CredentialSubject(
                        id = "did:key:holder123",
                        verified = true,
                        personalData = PersonalData(age = 30, nationality = "FR", documentType = "passport")
                    )
                ),
                createdAt = kotlin.time.Clock.System.now()
            )
        )
    }

    @Test
    fun `passed verification produces PASSED activity entry`() = runTest {
        storeCredential("cred-pass")

        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Childcare Ready",
            freshness = "2h",
            predicates = listOf("age_gte_18", "identity_verified"),
            predicateResults = listOf(
                PredicateResultDTO(predicateId = "age_gte_18", status = "satisfied"),
                PredicateResultDTO(predicateId = "identity_verified", status = "satisfied")
            ),
            summary = VerificationSummaryDTO(
                requiredSatisfied = 2,
                requiredTotal = 2,
                cachetGranted = true
            )
        )

        val result = useCase.verifyCredential("cred-pass", "childcare-es")

        val verification = result.getOrThrow()
        assertNotNull(verification.consentReceiptId)

        val receipts = receiptRepo.getAllReceipts().getOrThrow()
        assertEquals(1, receipts.size)

        val entry = ActivityMapper.toHistoryEntry(receipts.first())
        assertEquals(TrustStatus.PASSED, entry.status)
        assertEquals("Trust Pack verification: childcare-es", entry.title)
    }

    @Test
    fun `failed verification produces INCOMPLETE activity entry`() = runTest {
        storeCredential("cred-fail")

        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "",
            freshness = "2h",
            predicates = listOf("age_gte_18", "identity_verified"),
            predicateResults = listOf(
                PredicateResultDTO(predicateId = "age_gte_18", status = "satisfied"),
                PredicateResultDTO(predicateId = "identity_verified", status = "failed", reason = "no credential")
            ),
            summary = VerificationSummaryDTO(
                requiredSatisfied = 1,
                requiredTotal = 2,
                cachetGranted = false
            )
        )

        val result = useCase.verifyCredential("cred-fail", "childcare-es")

        val verification = result.getOrThrow()
        assertNotNull(verification.consentReceiptId)

        val receipts = receiptRepo.getAllReceipts().getOrThrow()
        assertEquals(1, receipts.size)

        val entry = ActivityMapper.toHistoryEntry(receipts.first())
        assertEquals(TrustStatus.INCOMPLETE, entry.status)
        assertEquals("Trust Pack verification: childcare-es", entry.title)
    }
}

// ── Test doubles ──

private class TestCredentialRepository : CredentialRepository {
    val creds = mutableListOf<StoredCredential>()
    override suspend fun storeCredential(credential: StoredCredential) { creds.add(credential) }
    override suspend fun getAllCredentials(): List<StoredCredential> = creds.toList()
    override suspend fun getCredentialById(localId: String) = creds.find { it.localId == localId }
    override suspend fun getCredentialsByIssuer(issuer: String) = creds.filter { it.credential.issuer == issuer }
    override suspend fun markCredentialRevoked(localId: String) {
        val i = creds.indexOfFirst { it.localId == localId }
        if (i >= 0) creds[i] = creds[i].copy(isRevoked = true)
    }
    override suspend fun deleteCredential(localId: String) { creds.removeAll { it.localId == localId } }
}

private class TestVerifierClient : VerifierClient {
    var verifyResponse: VerifyResponseDTO = VerifyResponseDTO(cachet = "", freshness = "fresh")

    override suspend fun listPacks(): List<PackSummary> = emptyList()
    override suspend fun createSession(packId: String?, question: String?, predicates: List<String>?) =
        VerificationSession(sessionId = "s", nonce = "n", verifierDid = "did:web:test")
    override suspend fun verifyPresentation(policyId: String, credentials: List<VerifiableCredentialDTO>) =
        verifyResponse
    override suspend fun verifySDJWTPresentation(policyId: String, sdJwtCredentials: List<String>, sessionId: String?) =
        verifyResponse
}

private class TestRelayClient : RelayClient {
    override suspend fun createSession(requestPayload: ByteArray) =
        RelaySession(sessionId = "s", requestUri = "/r", responseUri = "/r")
    override suspend fun fetchRequest(requestUri: String) = ByteArray(0)
    override suspend fun postResponse(responseUri: String, payload: ByteArray) {}
    override suspend fun pollResponse(responseUri: String): ByteArray? = null
}
