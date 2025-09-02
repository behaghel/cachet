package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.model.StoredCredential
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialRepositoryTest {
    
    private val repository = MockCredentialRepository()
    
    @Test
    fun testStoreCredential() = runTest {
        val credential = createTestCredential()
        val storedCredential = StoredCredential(
            localId = "local-123",
            credential = credential,
            createdAt = Clock.System.now()
        )
        
        repository.storeCredential(storedCredential)
        
        val retrieved = repository.getCredentialById("local-123")
        assertNotNull(retrieved)
        assertEquals(storedCredential.localId, retrieved.localId)
        assertEquals(storedCredential.credential.id, retrieved.credential.id)
    }
    
    @Test
    fun testGetAllCredentials() = runTest {
        val credential1 = createTestCredential("cred-1")
        val credential2 = createTestCredential("cred-2")
        
        repository.storeCredential(StoredCredential("local-1", credential1, Clock.System.now()))
        repository.storeCredential(StoredCredential("local-2", credential2, Clock.System.now()))
        
        val allCredentials = repository.getAllCredentials()
        assertEquals(2, allCredentials.size)
    }
    
    @Test
    fun testGetCredentialsByIssuer() = runTest {
        val issuer = "did:web:cachet.id"
        val credential1 = createTestCredential("cred-1", issuer)
        val credential2 = createTestCredential("cred-2", "did:web:other.id")
        
        repository.storeCredential(StoredCredential("local-1", credential1, Clock.System.now()))
        repository.storeCredential(StoredCredential("local-2", credential2, Clock.System.now()))
        
        val cachetCredentials = repository.getCredentialsByIssuer(issuer)
        assertEquals(1, cachetCredentials.size)
        assertEquals(issuer, cachetCredentials.first().credential.issuer)
    }
    
    @Test
    fun testMarkCredentialRevoked() = runTest {
        val credential = createTestCredential()
        val storedCredential = StoredCredential(
            localId = "local-123",
            credential = credential,
            createdAt = Clock.System.now()
        )
        
        repository.storeCredential(storedCredential)
        repository.markCredentialRevoked("local-123")
        
        val retrieved = repository.getCredentialById("local-123")
        assertNotNull(retrieved)
        assertTrue(retrieved.isRevoked)
    }
    
    @Test
    fun testDeleteCredential() = runTest {
        val credential = createTestCredential()
        val storedCredential = StoredCredential(
            localId = "local-123",
            credential = credential,
            createdAt = Clock.System.now()
        )
        
        repository.storeCredential(storedCredential)
        repository.deleteCredential("local-123")
        
        val retrieved = repository.getCredentialById("local-123")
        assertEquals(null, retrieved)
    }
    
    private fun createTestCredential(
        id: String = "urn:uuid:test-credential",
        issuer: String = "did:web:cachet.id"
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = issuer,
            issuanceDate = Clock.System.now(),
            credentialSubject = mapOf(
                "id" to "did:example:holder",
                "verified" to true
            )
        )
    }
}

// Mock repository for testing
class MockCredentialRepository : CredentialRepository {
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