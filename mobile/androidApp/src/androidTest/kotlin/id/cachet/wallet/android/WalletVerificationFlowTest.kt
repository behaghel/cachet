package id.cachet.wallet.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.cachet.wallet.android.ui.WalletApp
import id.cachet.wallet.android.ui.theme.CachetWalletTheme
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.android.ui.WalletViewModel
import id.cachet.wallet.domain.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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

    @Before
    fun setup() {
        // Stop any existing Koin instance
        stopKoin()

        // Create mock implementations
        mockCredentialRepository = MockCredentialRepository()
        mockVeriffIntegration = createMockVeriffIntegration()
        mockIssuanceUseCase = IssuanceUseCase(mockCredentialRepository, mockVeriffIntegration)

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
        factory { WalletViewModel(get()) }
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
        // Set up custom credential data
        val customData = mapOf(
            "id" to JsonPrimitive("did:example:testuser"),
            "name" to JsonPrimitive("Alice Johnson"),
            "nationality" to JsonPrimitive("CA"),
            "documentType" to JsonPrimitive("driver_license"),
            "verificationLevel" to JsonPrimitive("enhanced")
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

        // Verify custom credential data is displayed
        composeTestRule
            .onNodeWithText("Alice Johnson")
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
            credentialSubject = mapOf(
                "id" to JsonPrimitive("did:example:123456789"),
                "name" to JsonPrimitive("Test User"),
                "verified" to JsonPrimitive(true)
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