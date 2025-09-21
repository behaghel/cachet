package id.cachet.wallet.network

import id.cachet.wallet.domain.model.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenID4VCIClientTest {

    private val mockClient = MockOpenID4VCIClient()

    @Test
    fun tokenRequestReturnsAccessToken() = runSuspendTest {
        val response = mockClient.requestToken(
            clientId = "test-wallet",
            scope = "credential_issuance"
        )

        assertNotNull(response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(3600, response.expiresIn)
        assertEquals("credential_issuance", response.scope)
    }

    @Test
    fun credentialRequestRequiresValidToken() = runSuspendTest {
        val tokenResponse = mockClient.requestToken(
            clientId = "test-wallet",
            scope = "credential_issuance"
        )

        val credentialResponse = mockClient.requestCredential(
            accessToken = tokenResponse.accessToken,
            format = "jwt_vc",
            types = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-123"
        )

        assertEquals("jwt_vc", credentialResponse.format)
        val credential = credentialResponse.credential
        assertEquals("did:web:cachet.id", credential.issuer)
        assertTrue(credential.type.contains("IdentityCredential"))
        assertTrue(credential.credentialSubject.containsKey("verified"))
    }

    @Test
    fun credentialRequestWithInvalidTokenThrows() = runSuspendTest {
        assertFailsWith<OpenID4VCIException> {
            mockClient.requestCredential(
                accessToken = "invalid-token",
                format = "jwt_vc",
                types = listOf("VerifiableCredential"),
                sessionId = "session-123"
            )
        }
    }

    @Test
    fun credentialRequestSupportsDifferentFormats() = runSuspendTest {
        val tokenResponse = mockClient.requestToken("test-wallet", "credential_issuance")

        val credentialResponse = mockClient.requestCredential(
            accessToken = tokenResponse.accessToken,
            format = "ldp_vc",
            types = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-456"
        )

        assertEquals("ldp_vc", credentialResponse.format)
        assertNotNull(credentialResponse.credential)
    }

    @Test
    fun verificationStatusDefaultsToPending() = runSuspendTest {
        val status = mockClient.getVerificationStatus("session-789")
        assertEquals("pending", status.status)
    }

    private fun runSuspendTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }
}

private class MockOpenID4VCIClient : OpenID4VCIClient {
    private val validTokens = mutableSetOf<String>()
    private val sessionStatuses = mutableMapOf<String, VerificationStatusResponse>()

    override suspend fun requestToken(clientId: String, scope: String): TokenResponse {
        val token = "mock-access-token-${System.currentTimeMillis()}"
        validTokens.add(token)

        return TokenResponse(
            access_token = token,
            token_type = "Bearer",
            expires_in = 3600,
            scope = scope
        )
    }

    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>,
        sessionId: String
    ): CredentialResponse {
        if (!validTokens.contains(accessToken)) {
            throw OpenID4VCIException("Invalid access token")
        }

        sessionStatuses[sessionId] = VerificationStatusResponse(sessionId = sessionId, status = "approved")

        val mockCredential = VerifiableCredential(
            id = "urn:uuid:mock-credential-${System.currentTimeMillis()}",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:web:cachet.id",
            issuanceDate = Clock.System.now().toString(),
            credentialSubject = mapOf(
                "id" to JsonPrimitive("did:example:holder"),
                "verified" to JsonPrimitive(true),
                "verification_method" to JsonPrimitive("veriff")
            )
        )

        return CredentialResponse(
            credential = mockCredential,
            format = format
        )
    }

    override suspend fun getVerificationStatus(sessionId: String): VerificationStatusResponse {
        return sessionStatuses.getOrPut(sessionId) {
            VerificationStatusResponse(sessionId = sessionId, status = "pending")
        }
    }
}
