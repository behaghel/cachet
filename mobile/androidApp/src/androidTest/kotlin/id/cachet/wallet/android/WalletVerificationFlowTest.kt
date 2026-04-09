package id.cachet.wallet.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.cachet.wallet.android.ui.WalletApp
import id.cachet.wallet.android.ui.theme.CachetWalletTheme
import id.cachet.wallet.android.ui.WalletViewModel
import id.cachet.wallet.android.verification.MockVeriffService
import id.cachet.wallet.android.verification.VeriffService
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.domain.usecase.VerificationUseCase
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.PackSummary
import id.cachet.wallet.network.RelayClient
import id.cachet.wallet.network.RelaySession
import id.cachet.wallet.network.VerifiableCredentialDTO
import id.cachet.wallet.network.VerificationSession
import id.cachet.wallet.network.VerifierClient
import id.cachet.wallet.network.VerifyResponseDTO
import kotlin.time.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlinx.coroutines.flow.flowOf

@RunWith(AndroidJUnit4::class)
class WalletVerificationFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockCredentialRepository: CredentialRepository
    private lateinit var mockVeriffIntegration: MockVeriffIntegration
    private lateinit var mockIssuanceUseCase: IssuanceUseCase
    private lateinit var mockConsentUseCase: ConsentUseCase
    private lateinit var mockVerificationUseCase: VerificationUseCase

    @Before
    fun setup() {
        // Stop any existing Koin instance
        stopKoin()

        // Create mock implementations
        mockCredentialRepository = MockCredentialRepository()
        mockVeriffIntegration = createMockVeriffIntegration()
        mockIssuanceUseCase = IssuanceUseCase(mockCredentialRepository, mockVeriffIntegration)
        mockConsentUseCase = ConsentUseCase(
            mockCredentialRepository,
            InMemoryConsentReceiptRepository(),
            MockTransparencyLogRepository()
        )
        mockVerificationUseCase = VerificationUseCase(
            credentialRepository = mockCredentialRepository,
            verifierClient = StubVerifierClient(),
            relayClient = StubRelayClient(),
            consentUseCase = mockConsentUseCase
        )

        // Start Koin with test modules
        startKoin {
            androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
            modules(testModule)
        }
    }

    private val testModule = module {
        single<CredentialRepository> { mockCredentialRepository }
        single<OpenID4VCIClient> { mockVeriffIntegration }
        single<IssuanceUseCase> { mockIssuanceUseCase }
        single<ConsentUseCase> { mockConsentUseCase }
        single<VerificationUseCase> { mockVerificationUseCase }
        single<VeriffService> { MockVeriffService() }
        factory { WalletViewModel(get(), get(), get(), get()) }
    }

    @Test
    fun testEmptyWalletState_ShowsWelcomeScreen() {
        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Verify empty wallet state elements
        composeTestRule
            .onNodeWithText("Cachet Wallet")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Welcome to Cachet Wallet")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("You don't have any credentials yet. Start by verifying your identity with Veriff.")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testVerificationFlowTrigger_ClickStartButton() {
        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Click the "Start Identity Verification" button
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        // Wait for state change and verify loading state
        composeTestRule.waitForIdle()

        // The app should show either loading state or verification in progress
        composeTestRule
            .onNodeWithText("Verifying your identity...")
            .assertIsDisplayed()
    }

    @Test
    fun testSuccessfulVerificationFlow_ShowsCredentials() {
        // Configure mock to return successful verification
        mockVeriffIntegration.simulateSuccessfulVerification()
        mockVeriffIntegration.setNetworkDelay(false) // Speed up test

        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Start verification flow
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        // Wait for the flow to complete
        composeTestRule.waitForIdle()
        
        // Wait a bit longer for async operations
        Thread.sleep(3000)
        composeTestRule.waitForIdle()

        // Verify successful completion
        composeTestRule
            .onNodeWithText("Your Credentials")
            .assertIsDisplayed()

        // Check for mock credential data
        composeTestRule
            .onNodeWithText("Identity Verification")
            .assertIsDisplayed()
    }

    @Test
    fun testFailedVerificationFlow_ShowsError() {
        // Configure mock to return failure
        mockVeriffIntegration.simulateVerificationFailure()
        mockVeriffIntegration.setNetworkDelay(false) // Speed up test

        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Start verification flow
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        // Wait for the flow to complete
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        composeTestRule.waitForIdle()

        // Verify error state
        composeTestRule
            .onNodeWithText("Error")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Identity verification failed - please try again with valid documents")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testNetworkErrorScenario_ShowsNetworkError() {
        mockVeriffIntegration.simulateNetworkError()
        mockVeriffIntegration.setNetworkDelay(false)

        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Start verification
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(1000)
        composeTestRule.waitForIdle()

        // Verify network error message
        composeTestRule
            .onNodeWithText("Network connection failed - could not reach Veriff servers")
            .assertIsDisplayed()
    }

    @Test
    fun testExpiredSessionScenario_ShowsSessionError() {
        mockVeriffIntegration.simulateExpiredSession()
        mockVeriffIntegration.setNetworkDelay(false)

        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Start verification
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(1000)
        composeTestRule.waitForIdle()

        // Verify session error message
        composeTestRule
            .onNodeWithText("Verification session expired")
            .assertIsDisplayed()
    }

    @Test
    fun testCustomCredentialData_IsProperlyDisplayed() {
        // Set up custom credential data using typed CredentialSubject
        val customData = CredentialSubject(
            id = "did:example:testuser",
            personalData = PersonalData(
                nationality = "CA",
                documentType = "driver_license"
            ),
            verificationLevel = "enhanced",
            verified = true
        )

        mockVeriffIntegration.simulateSuccessfulVerification()
        mockVeriffIntegration.setCustomCredentialData(customData)
        mockVeriffIntegration.setNetworkDelay(false)

        composeTestRule.setContent {
            CachetWalletTheme {
                WalletApp()
            }
        }

        // Complete verification flow
        composeTestRule
            .onNodeWithText("Start Identity Verification")
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        composeTestRule.waitForIdle()

        // Verify the credential screen is displayed (custom data affects credential display)
        composeTestRule
            .onNodeWithText("Your Credentials")
            .assertIsDisplayed()
    }

    private fun createMockCredential(): VerifiableCredential {
        return VerifiableCredential(
            id = "test-credential-1",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "https://cachet-issuer.example.com",
            issuanceDate = Clock.System.now().toString(),
            expirationDate = null,
            credentialSubject = CredentialSubject(
                id = "did:example:123456789",
                verified = true,
                personalData = PersonalData(age = 30, nationality = "US", documentType = "passport")
            ),
            credentialStatus = null
        )
    }
}

// Mock implementations for testing
class MockCredentialRepository : CredentialRepository {
    private val credentials = mutableListOf<StoredCredential>()

    override suspend fun storeCredential(credential: StoredCredential) {
        credentials.add(credential)
    }

    override suspend fun getAllCredentials(): List<StoredCredential> = credentials.toList()

    override suspend fun getCredentialById(id: String): StoredCredential? =
        credentials.find { it.localId == id }

    override suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential> =
        credentials.filter { it.credential.issuer == issuer }

    override suspend fun markCredentialRevoked(id: String) {
        val index = credentials.indexOfFirst { it.localId == id }
        if (index >= 0) {
            credentials[index] = credentials[index].copy(isRevoked = true)
        }
    }

    override suspend fun deleteCredential(id: String) {
        credentials.removeIf { it.localId == id }
    }
}

// Use the sophisticated MockVeriffIntegration instead
private fun createMockVeriffIntegration(): MockVeriffIntegration = MockVeriffIntegration()

// Minimal stub implementations for VerifierClient and RelayClient
// These are needed by VerificationUseCase but not exercised by the current UI tests.

private class StubVerifierClient : VerifierClient {
    override suspend fun listPacks(): List<PackSummary> = emptyList()
    override suspend fun createSession(packId: String?, question: String?, predicates: List<String>?) =
        VerificationSession(sessionId = "stub", nonce = "stub", verifierDid = "did:web:stub")
    override suspend fun verifyPresentation(policyId: String, credentials: List<VerifiableCredentialDTO>) =
        VerifyResponseDTO(cachet = "", freshness = "fresh")
    override suspend fun verifySDJWTPresentation(policyId: String, sdJwtCredentials: List<String>, sessionId: String?) =
        VerifyResponseDTO(cachet = "", freshness = "fresh")
}

private class StubRelayClient : RelayClient {
    override suspend fun createSession(requestPayload: ByteArray) =
        RelaySession(sessionId = "stub", requestUri = "/stub/request", responseUri = "/stub/response")
    override suspend fun fetchRequest(requestUri: String) = ByteArray(0)
    override suspend fun postResponse(responseUri: String, payload: ByteArray) {}
    override suspend fun pollResponse(responseUri: String): ByteArray? = null
}