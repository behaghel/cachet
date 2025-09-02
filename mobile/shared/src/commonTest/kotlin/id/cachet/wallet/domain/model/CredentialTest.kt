package id.cachet.wallet.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialTest {
    
    @Test
    fun testCredentialCreation() {
        val now = Clock.System.now()
        val credential = VerifiableCredential(
            id = "urn:uuid:test-credential",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "did:web:cachet.id",
            issuanceDate = now,
            credentialSubject = mapOf(
                "id" to "did:example:holder",
                "verified" to true,
                "verification_method" to "veriff"
            )
        )
        
        assertEquals("urn:uuid:test-credential", credential.id)
        assertEquals("did:web:cachet.id", credential.issuer)
        assertTrue(credential.credentialSubject.containsKey("verified"))
        assertEquals(true, credential.credentialSubject["verified"])
    }
    
    @Test
    fun testCredentialStatus() {
        val status = CredentialStatus(
            id = "https://cachet.id/status/1#123",
            type = "StatusList2021Entry"
        )
        
        assertEquals("https://cachet.id/status/1#123", status.id)
        assertEquals("StatusList2021Entry", status.type)
    }
    
    @Test
    fun testCredentialWithStatus() {
        val now = Clock.System.now()
        val status = CredentialStatus(
            id = "https://cachet.id/status/1#123",
            type = "StatusList2021Entry"
        )
        
        val credential = VerifiableCredential(
            id = "urn:uuid:test-credential",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "did:web:cachet.id",
            issuanceDate = now,
            credentialSubject = mapOf("id" to "did:example:holder"),
            credentialStatus = status
        )
        
        assertEquals(status, credential.credentialStatus)
    }
}