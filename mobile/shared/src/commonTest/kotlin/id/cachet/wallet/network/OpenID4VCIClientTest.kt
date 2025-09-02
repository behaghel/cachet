package id.cachet.wallet.network

import id.cachet.wallet.domain.model.VerifiableCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenID4VCIClientTest {
    
    private val mockClient = MockOpenID4VCIClient()
    
    @Test
    fun testTokenRequest() = runTest {
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
    fun testCredentialRequest() = runTest {
        // First get token
        val tokenResponse = mockClient.requestToken(
            clientId = "test-wallet",
            scope = "credential_issuance"
        )
        
        // Then request credential
        val credentialResponse = mockClient.requestCredential(
            accessToken = tokenResponse.accessToken,
            format = "jwt_vc",
            types = listOf("VerifiableCredential", "IdentityCredential")
        )
        
        assertEquals("jwt_vc", credentialResponse.format)
        assertNotNull(credentialResponse.credential)
        
        val credential = credentialResponse.credential
        assertEquals("did:web:cachet.id", credential.issuer)
        assertTrue(credential.type.contains("IdentityCredential"))
        assertTrue(credential.credentialSubject.containsKey("verified"))
    }
    
    @Test
    fun testInvalidToken() = runTest {
        try {
            mockClient.requestCredential(
                accessToken = "invalid-token",
                format = "jwt_vc",
                types = listOf("VerifiableCredential")
            )
            assert(false) { "Should have thrown exception" }
        } catch (e: OpenID4VCIException) {
            assertEquals("Invalid access token", e.message)
        }
    }
    
    @Test
    fun testCredentialWithDifferentFormat() = runTest {
        val tokenResponse = mockClient.requestToken("test-wallet", "credential_issuance")
        
        val credentialResponse = mockClient.requestCredential(
            accessToken = tokenResponse.accessToken,
            format = "ldp_vc",
            types = listOf("VerifiableCredential", "IdentityCredential")
        )
        
        assertEquals("ldp_vc", credentialResponse.format)
        assertNotNull(credentialResponse.credential)
    }
}

// Mock client for testing
class MockOpenID4VCIClient : OpenID4VCIClient {
    private val validTokens = mutableSetOf<String>()
    
    override suspend fun requestToken(clientId: String, scope: String): TokenResponse {
        val token = "mock-access-token-${System.currentTimeMillis()}"
        validTokens.add(token)
        
        return TokenResponse(
            accessToken = token,
            tokenType = "Bearer",
            expiresIn = 3600,
            scope = scope
        )
    }
    
    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>
    ): CredentialResponse {
        if (!validTokens.contains(accessToken)) {
            throw OpenID4VCIException("Invalid access token")
        }
        
        val mockCredential = VerifiableCredential(
            id = "urn:uuid:mock-credential-${System.currentTimeMillis()}",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:web:cachet.id",
            issuanceDate = kotlinx.datetime.Clock.System.now(),
            credentialSubject = mapOf(
                "id" to "did:example:holder",
                "verified" to true,
                "verification_method" to "veriff"
            )
        )
        
        return CredentialResponse(
            credential = mockCredential,
            format = format
        )
    }
}