package id.cachet.wallet.domain.usecase

import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.FakeIssuanceQueue
import id.cachet.wallet.testfixtures.FakeKeyManager
import id.cachet.wallet.testfixtures.FakeOpenID4VCIClient
import id.cachet.wallet.testfixtures.makeCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuanceUseCaseOfflineTest {

    @Test
    fun `requestCredential enqueues when credential fetch fails after token success`() = runTest {
        val queue = FakeIssuanceQueue()
        val client = FakeOpenID4VCIClient(
            credentialError = RuntimeException("Network unavailable")
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            issuanceQueue = queue
        )

        val result = useCase.requestCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential"),
            sessionId = "session-1"
        )

        assertTrue(result.isFailure, "Should fail when credential fetch fails")
        assertEquals(1, queue.enqueuedStates.size, "Should enqueue partial state")
        val state = queue.enqueuedStates[0]
        assertEquals("test-client", state.clientId)
        assertEquals(listOf("VerifiableCredential"), state.credentialTypes)
        assertEquals("jwt_vc", state.format)
        assertEquals("session-1", state.sessionId)
        assertEquals("fake-token", state.accessToken)
        assertTrue(state.tokenExpiresAt > 0, "Should have valid expiry")
    }

    @Test
    fun `requestCredential does not enqueue when token request fails`() = runTest {
        val queue = FakeIssuanceQueue()
        val client = FakeOpenID4VCIClient(
            tokenError = RuntimeException("Token server unreachable")
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            issuanceQueue = queue
        )

        val result = useCase.requestCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential")
        )

        assertTrue(result.isFailure, "Should fail when token request fails")
        assertEquals(0, queue.enqueuedStates.size, "Should NOT enqueue when token failed")
    }

    @Test
    fun `requestCredential does not crash when no queue configured`() = runTest {
        val client = FakeOpenID4VCIClient(
            credentialError = RuntimeException("Network unavailable")
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            issuanceQueue = null
        )

        val result = useCase.requestCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential")
        )

        assertTrue(result.isFailure, "Should fail gracefully without queue")
    }

    @Test
    fun `requestSDJWTCredential enqueues when credential fetch fails after token success`() = runTest {
        val queue = FakeIssuanceQueue()
        val client = FakeOpenID4VCIClient(
            sdJwtCredentialError = RuntimeException("Network unavailable")
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            keyManager = FakeKeyManager(),
            issuanceQueue = queue
        )

        val result = useCase.requestSDJWTCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-2"
        )

        assertTrue(result.isFailure, "Should fail when SD-JWT credential fetch fails")
        assertEquals(1, queue.enqueuedStates.size, "Should enqueue partial state")
        val state = queue.enqueuedStates[0]
        assertEquals("test-client", state.clientId)
        assertEquals("vc+sd-jwt", state.format)
        assertEquals("session-2", state.sessionId)
        assertEquals("fake-token", state.accessToken)
        assertTrue(state.keyAlias != null, "Should preserve key alias")
        assertTrue(state.holderJwk != null, "Should preserve holder JWK")
    }

    @Test
    fun `requestSDJWTCredential does not enqueue when token request fails`() = runTest {
        val queue = FakeIssuanceQueue()
        val client = FakeOpenID4VCIClient(
            tokenError = RuntimeException("Token server unreachable")
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            keyManager = FakeKeyManager(),
            issuanceQueue = queue
        )

        val result = useCase.requestSDJWTCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential")
        )

        assertTrue(result.isFailure, "Should fail when token request fails")
        assertEquals(0, queue.enqueuedStates.size, "Should NOT enqueue when token failed")
    }

    @Test
    fun `requestCredential succeeds normally without enqueuing`() = runTest {
        val queue = FakeIssuanceQueue()
        val client = FakeOpenID4VCIClient(
            credentialResponse = CredentialResponse(
                credential = makeCredential(),
                format = "jwt_vc"
            )
        )
        val useCase = IssuanceUseCase(
            credentialRepository = FakeCredentialRepository(),
            openID4VCIClient = client,
            issuanceQueue = queue
        )

        val result = useCase.requestCredential(
            clientId = "test-client",
            credentialTypes = listOf("VerifiableCredential")
        )

        assertTrue(result.isSuccess, "Should succeed normally")
        assertEquals(0, queue.enqueuedStates.size, "Should NOT enqueue on success")
    }
}
