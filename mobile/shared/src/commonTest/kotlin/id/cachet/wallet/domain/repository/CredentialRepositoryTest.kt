package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.model.StoredCredential
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialRepositoryTest {
    
    private val repository = MockCredentialRepository()
    
    @Test
    fun testStoreCredential() = runSuspendTest {
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
    fun testGetAllCredentials() = runSuspendTest {
        val credential1 = createTestCredential("cred-1")
        val credential2 = createTestCredential("cred-2")
        
        repository.storeCredential(
            StoredCredential(
                localId = "local-1",
                credential = credential1,
                createdAt = Clock.System.now()
            )
        )
        repository.storeCredential(
            StoredCredential(
                localId = "local-2",
                credential = credential2,
                createdAt = Clock.System.now()
            )
        )
        
        val allCredentials = repository.getAllCredentials()
        assertEquals(2, allCredentials.size)
    }
    
    @Test
    fun testGetCredentialsByIssuer() = runSuspendTest {
        val issuer = "did:web:cachet.id"
        val credential1 = createTestCredential("cred-1", issuer)
        val credential2 = createTestCredential("cred-2", "did:web:other.id")
        
        repository.storeCredential(
            StoredCredential(
                localId = "local-1",
                credential = credential1,
                createdAt = Clock.System.now()
            )
        )
        repository.storeCredential(
            StoredCredential(
                localId = "local-2",
                credential = credential2,
                createdAt = Clock.System.now()
            )
        )
        
        val cachetCredentials = repository.getCredentialsByIssuer(issuer)
        assertEquals(1, cachetCredentials.size)
        assertEquals(issuer, cachetCredentials.first().credential.issuer)
    }
    
    @Test
    fun testMarkCredentialRevoked() = runSuspendTest {
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
    fun testDeleteCredential() = runSuspendTest {
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
            issuanceDate = Clock.System.now().toString(),
            credentialSubject = mapOf(
                "id" to JsonPrimitive("did:example:holder"),
                "verified" to JsonPrimitive(true)
            )
        )
    }

    private fun runSuspendTest(block: suspend () -> Unit) {
        runBlocking { block() }
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
