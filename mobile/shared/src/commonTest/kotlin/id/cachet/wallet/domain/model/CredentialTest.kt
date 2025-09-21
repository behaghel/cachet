package id.cachet.wallet.domain.model

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.hours

class CredentialTest {

    @Test
    fun credentialCreationPopulatesFields() {
        val now = Clock.System.now()
        val credential = VerifiableCredential(
            id = "urn:uuid:test-credential",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "IdentityCredential"),
            issuer = "did:web:cachet.id",
            issuanceDate = now.toString(),
            credentialSubject = mapOf(
                "id" to JsonPrimitive("did:example:holder"),
                "verified" to JsonPrimitive(true),
                "verification_method" to JsonPrimitive("veriff")
            )
        )

        assertEquals("urn:uuid:test-credential", credential.id)
        assertEquals("did:web:cachet.id", credential.issuer)
        assertEquals(now.toString(), credential.issuanceDate)
        assertEquals("did:example:holder", credential.getSubjectId())
    }

    @Test
    fun credentialStatusRoundTrips() {
        val status = CredentialStatus(
            id = "https://cachet.id/status/1#123",
            type = "StatusList2021Entry"
        )

        assertEquals("https://cachet.id/status/1#123", status.id)
        assertEquals("StatusList2021Entry", status.type)
    }

    @Test
    fun credentialWithStatusExposesHelpers() {
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
            issuanceDate = now.toString(),
            expirationDate = (now + 1.hours).toString(),
            credentialSubject = mapOf("id" to JsonPrimitive("did:example:holder")),
            credentialStatus = status
        )

        assertEquals(status, credential.credentialStatus)
        assertNotNull(credential.getIssuanceInstant())
        assertFalse(credential.isExpired())
    }
}
