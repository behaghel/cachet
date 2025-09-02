package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.TokenResponse
import id.cachet.wallet.network.CredentialResponse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuanceUseCaseTest {
    
    private val mockRepository = MockCredentialRepository()
    private val mockClient = MockOpenID4VCIClient()
    private val issuanceUseCase = IssuanceUseCase(mockRepository, mockClient)
    
    @Test
    fun testSuccessfulIssuance() = runTest {
        val result = issuanceUseCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential")
        )
        
        assertTrue(result.isSuccess)
        val storedCredential = result.getOrNull()
        assertNotNull(storedCredential)
        assertEquals("did:web:cachet.id", storedCredential.credential.issuer)
        assertTrue(storedCredential.credential.type.contains("IdentityCredential"))
        
        // Verify credential was stored in repository
        val repositoryCredential = mockRepository.getCredentialById(storedCredential.localId)
        assertNotNull(repositoryCredential)
        assertEquals(storedCredential.localId, repositoryCredential.localId)
    }
    
    @Test
    fun testNetworkFailureDuringTokenRequest() = runTest {
        val failingClient = FailingOpenID4VCIClient(failAtToken = true)
        val useCase = IssuanceUseCase(mockRepository, failingClient)
        
        val result = useCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential")
        )
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception.message?.contains("Token request failed") == true)
    }
    
    @Test
    fun testNetworkFailureDuringCredentialRequest() = runTest {
        val failingClient = FailingOpenID4VCIClient(failAtCredential = true)
        val useCase = IssuanceUseCase(mockRepository, failingClient)
        
        val result = useCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential")
        )
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception.message?.contains("Credential request failed") == true)
    }
    
    @Test
    fun testIssuanceWithDifferentCredentialFormat() = runTest {
        val result = issuanceUseCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            format = "ldp_vc"
        )
        
        assertTrue(result.isSuccess)
        val storedCredential = result.getOrNull()
        assertNotNull(storedCredential)
    }
    
    @Test 
    fun testMultipleCredentialTypes() = runTest {
        val result = issuanceUseCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential", "ProofOfAge")
        )
        
        assertTrue(result.isSuccess)
        val storedCredential = result.getOrNull()
        assertNotNull(storedCredential)
        assertEquals(3, storedCredential.credential.type.size)
        assertTrue(storedCredential.credential.type.contains("ProofOfAge"))
    }
}

// Mock implementations for testing
private class MockCredentialRepository : CredentialRepository {
    private val credentials = mutableMapOf<String, StoredCredential>()
    
    override suspend fun storeCredential(credential: StoredCredential) {
        credentials[credential.localId] = credential
    }
    
    override suspend fun getAllCredentials(): List<StoredCredential> {
        return credentials.values.sortedByDescending { it.createdAt }
    }
    
    override suspend fun getCredentialById(localId: String): StoredCredential? {
        return credentials[localId]
    }
    
    override suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential> {
        return credentials.values
            .filter { it.credential.issuer == issuer }
            .sortedByDescending { it.createdAt }
    }
    
    override suspend fun markCredentialRevoked(localId: String) {
        val credential = credentials[localId] ?: return
        credentials[localId] = credential.copy(isRevoked = true)
    }
    
    override suspend fun deleteCredential(localId: String) {
        credentials.remove(localId)
    }
}

private class MockOpenID4VCIClient : OpenID4VCIClient {
    private val validTokens = mutableSetOf<String>()
    
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
        types: List<String>
    ): CredentialResponse {
        if (!validTokens.contains(accessToken)) {
            throw Exception("Invalid access token")
        }
        
        val mockCredential = VerifiableCredential(
            id = "urn:uuid:mock-credential-${System.currentTimeMillis()}",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:web:cachet.id",
            issuanceDate = Clock.System.now(),
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

private class FailingOpenID4VCIClient(
    private val failAtToken: Boolean = false,
    private val failAtCredential: Boolean = false
) : OpenID4VCIClient {
    
    override suspend fun requestToken(clientId: String, scope: String): TokenResponse {
        if (failAtToken) {
            throw Exception("Token request failed")
        }
        
        return TokenResponse(
            access_token = "valid-token",
            token_type = "Bearer",
            expires_in = 3600,
            scope = scope
        )
    }
    
    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>
    ): CredentialResponse {
        if (failAtCredential) {
            throw Exception("Credential request failed")
        }
        
        val mockCredential = VerifiableCredential(
            id = "urn:uuid:mock-credential",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:web:cachet.id",
            issuanceDate = Clock.System.now(),
            credentialSubject = mapOf("id" to "did:example:holder")
        )
        
        return CredentialResponse(credential = mockCredential, format = format)
    }
}