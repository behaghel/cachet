package id.cachet.wallet.android.ui

import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.mapper.ActivityMapper
import id.cachet.wallet.android.verification.VeriffResult
import id.cachet.wallet.android.verification.VeriffService
import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the verification → activity logging chain through the ViewModel.
 *
 * The previous iteration of this test only covered [VerificationUseCase.verifyCredential],
 * but the real emulator flow goes through [WalletViewModel.awaitVerifierResult] which has
 * its own receipt generation logic. These tests exercise that actual code path — including
 * the exception/catch branch — so regressions on activity logging are caught.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationActivityIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppConfig.configure(relayUrl = "http://test-relay")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppConfig.reset()
    }

    // ── VerificationUseCase.verifyCredential path (direct flow) ──

    @Test
    fun `verifyCredential - passed verification produces PASSED receipt`() = runTest {
        val credRepo = FakeCredRepo()
        val receiptRepo = InMemoryConsentReceiptRepository()
        val consentUseCase = ConsentUseCase(credRepo, receiptRepo, MockTransparencyLogRepository())
        val verifierClient = ConfigurableVerifierClient()
        val useCase = VerificationUseCase(
            credentialRepository = credRepo,
            verifierClient = verifierClient,
            relayClient = ConfigurableRelayClient(),
            consentUseCase = consentUseCase
        )

        credRepo.store("cred-1")
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Childcare Ready", freshness = "2h",
            predicates = listOf("age_gte_18"),
            predicateResults = listOf(PredicateResultDTO("age_gte_18", "satisfied")),
            summary = VerificationSummaryDTO(requiredSatisfied = 1, requiredTotal = 1, cachetGranted = true)
        )

        useCase.verifyCredential("cred-1", "childcare-es").getOrThrow()

        val receipt = receiptRepo.getAllReceipts().getOrThrow().single()
        assertEquals(ConsentReceipt.OUTCOME_PASSED, receipt.outcome)
        assertEquals(TrustStatus.PASSED, ActivityMapper.toHistoryEntry(receipt).status)
    }

    @Test
    fun `verifyCredential - failed verification produces INCOMPLETE receipt`() = runTest {
        val credRepo = FakeCredRepo()
        val receiptRepo = InMemoryConsentReceiptRepository()
        val consentUseCase = ConsentUseCase(credRepo, receiptRepo, MockTransparencyLogRepository())
        val verifierClient = ConfigurableVerifierClient()
        val useCase = VerificationUseCase(
            credentialRepository = credRepo,
            verifierClient = verifierClient,
            relayClient = ConfigurableRelayClient(),
            consentUseCase = consentUseCase
        )

        credRepo.store("cred-1")
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "", freshness = "2h",
            predicates = listOf("age_gte_18"),
            predicateResults = listOf(PredicateResultDTO("age_gte_18", "failed")),
            summary = VerificationSummaryDTO(requiredSatisfied = 0, requiredTotal = 1, cachetGranted = false)
        )

        useCase.verifyCredential("cred-1", "childcare-es").getOrThrow()

        val receipt = receiptRepo.getAllReceipts().getOrThrow().single()
        assertEquals(ConsentReceipt.OUTCOME_INCOMPLETE, receipt.outcome)
        assertEquals(TrustStatus.INCOMPLETE, ActivityMapper.toHistoryEntry(receipt).status)
    }

    // ── WalletViewModel.awaitVerifierResult path (relay flow — the emulator path) ──

    private fun createViewModel(
        verifierClient: ConfigurableVerifierClient = ConfigurableVerifierClient(),
        relayClient: ConfigurableRelayClient = ConfigurableRelayClient()
    ): Triple<WalletViewModel, ConfigurableVerifierClient, InMemoryConsentReceiptRepository> {
        val credRepo = FakeCredRepo()
        val receiptRepo = InMemoryConsentReceiptRepository()
        val consentUseCase = ConsentUseCase(credRepo, receiptRepo, MockTransparencyLogRepository())
        val verificationUseCase = VerificationUseCase(
            credentialRepository = credRepo,
            verifierClient = verifierClient,
            relayClient = relayClient,
            consentUseCase = consentUseCase
        )
        val vm = WalletViewModel(
            issuanceUseCase = IssuanceUseCase(credRepo, NoOpOpenID4VCIClient()),
            veriffService = NoOpVeriffService(),
            consentUseCase = consentUseCase,
            verificationUseCase = verificationUseCase,
            demoMode = true,
            demoEmpty = false
        )
        return Triple(vm, verifierClient, receiptRepo)
    }

    @Test
    fun `awaitVerifierResult - cachet granted produces PASSED activity entry`() = runTest {
        val verifierClient = ConfigurableVerifierClient()
        val relayClient = ConfigurableRelayClient()
        relayClient.pollResult = "presentation-jwt".encodeToByteArray()
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Childcare Ready", freshness = "2h",
            predicateResults = listOf(PredicateResultDTO("age_gte_18", "satisfied")),
            summary = VerificationSummaryDTO(requiredSatisfied = 1, requiredTotal = 1, cachetGranted = true)
        )
        val (vm, _, _) = createViewModel(verifierClient, relayClient)

        // Set up the active session
        vm.createVerifierSession("childcare-es", "Are you safe?", listOf("age_gte_18"))

        // Relay returns verification result
        val result = vm.awaitVerifierResult()
        assertTrue(result.allPassed)

        // The activity entry must reflect PASSED
        val entry = vm.activityState.value.historyGroups
            .flatMap { it.entries }
            .first { it.time == "Just now" }
        assertEquals(TrustStatus.PASSED, entry.status)
    }

    @Test
    fun `awaitVerifierResult - cachet NOT granted produces INCOMPLETE activity entry`() = runTest {
        val verifierClient = ConfigurableVerifierClient()
        val relayClient = ConfigurableRelayClient()
        relayClient.pollResult = "presentation-jwt".encodeToByteArray()
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "", freshness = "2h",
            predicateResults = listOf(
                PredicateResultDTO("age_gte_18", "satisfied"),
                PredicateResultDTO("identity_verified", "failed", "no credential")
            ),
            summary = VerificationSummaryDTO(requiredSatisfied = 1, requiredTotal = 2, cachetGranted = false)
        )
        val (vm, _, _) = createViewModel(verifierClient, relayClient)

        vm.createVerifierSession("childcare-es", "Are you safe?", listOf("age_gte_18", "identity_verified"))
        val result = vm.awaitVerifierResult()
        assertTrue(!result.allPassed)

        val entry = vm.activityState.value.historyGroups
            .flatMap { it.entries }
            .first { it.time == "Just now" }
        assertEquals(TrustStatus.INCOMPLETE, entry.status)
    }

    @Test
    fun `awaitVerifierResult - relay exception still produces activity entry`() = runTest {
        val verifierClient = ConfigurableVerifierClient()
        val relayClient = ConfigurableRelayClient()
        relayClient.pollException = Exception("Relay timed out")
        val (vm, _, _) = createViewModel(verifierClient, relayClient)

        vm.createVerifierSession("childcare-es", "Are you safe?", listOf("age_gte_18"))
        val result = vm.awaitVerifierResult()
        assertTrue(result.isError)

        // KEY ASSERTION: even on error, an activity entry must exist
        val entries = vm.activityState.value.historyGroups
            .flatMap { it.entries }
            .filter { it.time == "Just now" }
        assertTrue(
            "Failed relay verification must still produce an activity entry",
            entries.isNotEmpty()
        )
        assertEquals(TrustStatus.INCOMPLETE, entries.first().status)
    }
}

// ── Configurable test doubles ──

private class ConfigurableVerifierClient : VerifierClient {
    var verifyResponse: VerifyResponseDTO = VerifyResponseDTO(cachet = "", freshness = "fresh")

    override suspend fun listPacks(): List<PackSummary> = emptyList()
    override suspend fun createSession(packId: String?, question: String?, predicates: List<String>?) =
        VerificationSession(sessionId = "s", nonce = "n", verifierDid = "did:web:test")
    override suspend fun verifyPresentation(policyId: String, credentials: List<VerifiableCredentialDTO>) =
        verifyResponse
    override suspend fun verifySDJWTPresentation(policyId: String, sdJwtCredentials: List<String>, sessionId: String?) =
        verifyResponse
}

private class ConfigurableRelayClient : RelayClient {
    var pollResult: ByteArray? = null
    var pollException: Exception? = null

    override suspend fun createSession(requestPayload: ByteArray) =
        RelaySession(sessionId = "s", requestUri = "/sessions/s/request", responseUri = "/sessions/s/response")
    override suspend fun fetchRequest(requestUri: String) = ByteArray(0)
    override suspend fun postResponse(responseUri: String, payload: ByteArray) {}
    override suspend fun pollResponse(responseUri: String): ByteArray? {
        pollException?.let { throw it }
        return pollResult
    }
}

private class FakeCredRepo : CredentialRepository {
    private val creds = mutableListOf<StoredCredential>()
    fun store(localId: String) {
        creds.add(makeCredential(localId))
    }
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

private class NoOpOpenID4VCIClient : OpenID4VCIClient {
    override suspend fun requestToken(clientId: String, scope: String, sessionId: String?) =
        TokenResponse(access_token = "tok", token_type = "Bearer", expires_in = 3600, scope = "openid")
    override suspend fun requestCredential(accessToken: String, format: String, types: List<String>): CredentialResponse =
        throw OpenID4VCIException("stub")
    override suspend fun requestSDJWTCredential(accessToken: String, types: List<String>, holderJWK: String): SDJWTCredentialResponse =
        throw OpenID4VCIException("stub")
}

private class NoOpVeriffService : VeriffService {
    override suspend fun startVerification(): VeriffResult = VeriffResult.Cancelled
}

private fun makeCredential(localId: String) = StoredCredential(
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
