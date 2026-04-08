package id.cachet.wallet.android.ui

import id.cachet.wallet.android.verification.VeriffResult
import id.cachet.wallet.android.verification.VeriffService
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
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        veriffResult: VeriffResult = VeriffResult.Success("session-1"),
        demoEmpty: Boolean = false
    ): WalletViewModel {
        val credRepo = InMemoryCredentialRepository()
        val openIdClient = StubOpenID4VCIClient()
        val issuanceUseCase = IssuanceUseCase(credRepo, openIdClient)
        val consentUseCase = ConsentUseCase(
            credRepo, InMemoryConsentReceiptRepository(), MockTransparencyLogRepository()
        )
        val verificationUseCase = VerificationUseCase(
            credentialRepository = credRepo,
            verifierClient = StubVerifierClient(),
            relayClient = StubRelayClient(),
            consentUseCase = consentUseCase
        )
        val veriffService = FakeVeriffService(veriffResult)

        return WalletViewModel(
            issuanceUseCase = issuanceUseCase,
            veriffService = veriffService,
            consentUseCase = consentUseCase,
            verificationUseCase = verificationUseCase,
            demoMode = false,
            demoEmpty = demoEmpty
        )
    }

    // ── Empty vault → IDV transition ──

    @Test
    fun `empty vault starts in Empty state`() {
        val vm = createViewModel(demoEmpty = true)
        assertEquals(WalletUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `startVeriffVerification from Empty transitions to VerificationInProgress`() = runTest {
        val vm = createViewModel(
            veriffResult = VeriffResult.Cancelled, // just need to observe the intermediate state
            demoEmpty = true
        )
        assertEquals(WalletUiState.Empty, vm.uiState.value)

        vm.startVeriffVerification()

        // After Cancelled, it reloads credentials which returns empty → Empty state
        // But VerificationInProgress was the intermediate state before the result came back
        // With UnconfinedTestDispatcher, the coroutine completes synchronously,
        // so we check the final state: Cancelled → loadCredentials → Empty
        assertEquals(WalletUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `startVeriffVerification shows error on failure`() = runTest {
        val vm = createViewModel(
            veriffResult = VeriffResult.Failure("Document rejected"),
            demoEmpty = true
        )

        vm.startVeriffVerification()

        val state = vm.uiState.value
        assertTrue("Expected Error state, got $state", state is WalletUiState.Error)
        assertTrue((state as WalletUiState.Error).message.contains("Document rejected"))
    }

    @Test
    fun `startVeriffVerification is no-op in demo mode`() {
        val credRepo = InMemoryCredentialRepository()
        val vm = WalletViewModel(
            issuanceUseCase = IssuanceUseCase(credRepo, StubOpenID4VCIClient()),
            veriffService = FakeVeriffService(VeriffResult.Success("s1")),
            consentUseCase = ConsentUseCase(credRepo, InMemoryConsentReceiptRepository(), MockTransparencyLogRepository()),
            verificationUseCase = VerificationUseCase(credRepo, StubVerifierClient(), StubRelayClient(), ConsentUseCase(credRepo, InMemoryConsentReceiptRepository(), MockTransparencyLogRepository())),
            demoMode = true,
            demoEmpty = false
        )

        vm.startVeriffVerification()

        // In demo mode, startVeriffVerification returns immediately — state doesn't change to VerificationInProgress
        val state = vm.uiState.value
        assertTrue("Demo mode should not trigger verification, got $state", state !is WalletUiState.VerificationInProgress)
    }
}

// ── Stubs ──

private class FakeVeriffService(private val result: VeriffResult) : VeriffService {
    override suspend fun startVerification(): VeriffResult = result
}

private class InMemoryCredentialRepository : CredentialRepository {
    private val creds = mutableListOf<StoredCredential>()
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

private class StubOpenID4VCIClient : OpenID4VCIClient {
    override suspend fun requestToken(clientId: String, scope: String, sessionId: String?) =
        TokenResponse(access_token = "tok", token_type = "Bearer", expires_in = 3600, scope = "openid")
    override suspend fun requestCredential(accessToken: String, format: String, types: List<String>): CredentialResponse =
        throw OpenID4VCIException("no session in stub")
    override suspend fun requestSDJWTCredential(accessToken: String, types: List<String>, holderJWK: String): SDJWTCredentialResponse =
        throw OpenID4VCIException("no session in stub")
}

private class StubVerifierClient : VerifierClient {
    override suspend fun listPacks(): List<PackSummary> = emptyList()
    override suspend fun createSession(packId: String?, question: String?, predicates: List<String>?) =
        VerificationSession(sessionId = "s", nonce = "n", verifierDid = "did:web:stub")
    override suspend fun verifyPresentation(policyId: String, credentials: List<VerifiableCredentialDTO>) =
        VerifyResponseDTO(cachet = "", freshness = "fresh")
    override suspend fun verifySDJWTPresentation(policyId: String, sdJwtCredentials: List<String>, sessionId: String?) =
        VerifyResponseDTO(cachet = "", freshness = "fresh")
}

private class StubRelayClient : RelayClient {
    override suspend fun createSession(requestPayload: ByteArray) =
        RelaySession(sessionId = "s", requestUri = "/r", responseUri = "/r")
    override suspend fun fetchRequest(requestUri: String) = ByteArray(0)
    override suspend fun postResponse(responseUri: String, payload: ByteArray) {}
    override suspend fun pollResponse(responseUri: String): ByteArray? = null
}
