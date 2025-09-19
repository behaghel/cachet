package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.model.VaultArtifact
import id.cachet.wallet.domain.model.VaultPredicate
import id.cachet.wallet.domain.repository.VaultRepository
import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.TokenResponse
import id.cachet.wallet.network.VerificationStatusResponse
import id.cachet.wallet.network.VaultArtifactDTO
import id.cachet.wallet.network.VaultPredicateDTO
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuanceUseCaseTest {
    
    private val mockRepository = MockCredentialRepository()
    private val mockVaultRepository = MockVaultRepository()
    private val mockClient = MockOpenID4VCIClient()
    private val issuanceUseCase = IssuanceUseCase(mockRepository, mockClient, mockVaultRepository)
    
    @Test
    fun testSuccessfulIssuance() = runTest {
        val result = issuanceUseCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-success"
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
        val useCase = IssuanceUseCase(mockRepository, failingClient, mockVaultRepository)
        
        val result = useCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-token-fail"
        )
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception.message?.contains("Token request failed") == true)
    }
    
    @Test
    fun testNetworkFailureDuringCredentialRequest() = runTest {
        val failingClient = FailingOpenID4VCIClient(failAtCredential = true)
        val useCase = IssuanceUseCase(mockRepository, failingClient, mockVaultRepository)
        
        val result = useCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "session-credential-fail"
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
            format = "ldp_vc",
            sessionId = "session-ldp"
        )
        
        assertTrue(result.isSuccess)
        val storedCredential = result.getOrNull()
        assertNotNull(storedCredential)
    }

    @Test
    fun testWaitForVerificationApproval() = runTest {
        val result = issuanceUseCase.waitForVerificationApproval("session-success", maxAttempts = 1, delayMillis = 10)
        assertTrue(result.isSuccess)
    }
    
    @Test 
    fun testMultipleCredentialTypes() = runTest {
        val result = issuanceUseCase.requestCredential(
            clientId = "test-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential", "ProofOfAge"),
            sessionId = "session-multi"
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
        types: List<String>,
        sessionId: String
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
                "verification_method" to "veriff",
                "sessionId" to sessionId
            )
        )

        val issuedAt = Clock.System.now().epochSeconds
        val artifactDto = VaultArtifactDTO(
            id = "veriff-$sessionId",
            type = "veriff-session",
            source = "veriff",
            payload = buildJsonObject {
                put("sessionId", JsonPrimitive(sessionId))
                put("decision", JsonPrimitive("approved"))
            },
            createdAt = issuedAt
        )

        val predicateDto = VaultPredicateDTO(
            id = "age-$sessionId",
            key = "age.ge.18",
            value = "true",
            proofType = "veriff",
            issuedAt = issuedAt,
            expiresAt = null,
            artifact = artifactDto
        )
        
        return CredentialResponse(
            credential = mockCredential,
            format = format,
            vaultArtifacts = listOf(artifactDto),
            vaultPredicates = listOf(predicateDto)
        )
    }

    override suspend fun getVerificationStatus(sessionId: String): VerificationStatusResponse {
        return VerificationStatusResponse(sessionId = sessionId, status = "approved")
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
        types: List<String>,
        sessionId: String
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
            credentialSubject = mapOf(
                "id" to "did:example:holder",
                "sessionId" to sessionId
            )
        )

        return CredentialResponse(credential = mockCredential, format = format)
    }

    override suspend fun getVerificationStatus(sessionId: String): VerificationStatusResponse {
        if (failAtCredential) {
            throw Exception("Status request failed")
        }
        return VerificationStatusResponse(sessionId = sessionId, status = "approved")
    }
}

private class MockVaultRepository : VaultRepository {
    private val artifacts = mutableMapOf<String, VaultArtifact>()
    private val predicates = mutableMapOf<String, VaultPredicate>()

    override suspend fun upsertArtifacts(artifacts: List<VaultArtifact>) {
        artifacts.forEach { this.artifacts[it.id] = it }
    }

    override suspend fun upsertPredicates(predicates: List<VaultPredicate>) {
        predicates.forEach { predicate ->
            this.predicates[predicate.id] = predicate.copy(
                artifact = predicate.artifact?.let { artifacts[it.id] ?: it }
            )
        }
    }

    override suspend fun getAllPredicates(): List<VaultPredicate> = predicates.values.toList()

    override suspend fun clear() {
        artifacts.clear()
        predicates.clear()
    }
}
