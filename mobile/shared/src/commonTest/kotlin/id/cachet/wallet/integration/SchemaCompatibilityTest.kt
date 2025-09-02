package id.cachet.wallet.integration

import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.network.TokenResponse
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

/**
 * Integration tests to ensure Kotlin models are compatible with backend API responses.
 * These tests validate that our data models can correctly deserialize actual backend responses.
 */
class SchemaCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `TokenResponse deserializes from backend JSON correctly`() {
        // Actual JSON response from issuance gateway
        val backendResponse = """
        {
            "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
            "token_type": "Bearer",
            "expires_in": 3600,
            "scope": "credential_issuance"
        }
        """.trimIndent()

        val tokenResponse = json.decodeFromString<TokenResponse>(backendResponse)

        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...", tokenResponse.access_token)
        assertEquals("Bearer", tokenResponse.token_type)
        assertEquals(3600, tokenResponse.expires_in)
        assertEquals("credential_issuance", tokenResponse.scope)

        // Test property accessors
        assertEquals(tokenResponse.access_token, tokenResponse.accessToken)
        assertEquals(tokenResponse.token_type, tokenResponse.tokenType)
        assertEquals(tokenResponse.expires_in, tokenResponse.expiresIn)
    }

    @Test
    fun `VerifiableCredential deserializes with @context field correctly`() {
        // Actual JSON response from backend with @context field
        val backendResponse = """
        {
            "id": "urn:uuid:3fc541a8-103c-4f9c-8d54-9a7a72aed350",
            "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://cachet.id/contexts/identity/v1"
            ],
            "type": ["VerifiableCredential", "IdentityCredential"],
            "issuer": "did:web:cachet.id",
            "issuanceDate": "2025-09-01T21:27:40+02:00",
            "credentialSubject": {
                "id": "did:example:holder",
                "verified": true,
                "verification_method": "veriff",
                "verification_level": "identity_document_liveness"
            },
            "credentialStatus": {
                "id": "https://cachet.id/status/1#1e99bbcd-5be8-4bf4-9dff-88cf13fb25b8",
                "type": "StatusList2021Entry"
            }
        }
        """.trimIndent()

        val credential = json.decodeFromString<VerifiableCredential>(backendResponse)

        // Validate all required fields
        assertEquals("urn:uuid:3fc541a8-103c-4f9c-8d54-9a7a72aed350", credential.id)
        assertEquals(2, credential.context.size)
        assertEquals("https://www.w3.org/2018/credentials/v1", credential.context[0])
        assertEquals(2, credential.type.size)
        assertEquals("VerifiableCredential", credential.type[0])
        assertEquals("did:web:cachet.id", credential.issuer)
        assertEquals("2025-09-01T21:27:40+02:00", credential.issuanceDate)

        // Validate credentialSubject as JsonElement
        assertNotNull(credential.credentialSubject["id"])
        assertNotNull(credential.credentialSubject["verified"])
        assertEquals("did:example:holder", credential.getSubjectId())

        // Validate credentialStatus
        assertNotNull(credential.credentialStatus)
        assertEquals("StatusList2021Entry", credential.credentialStatus?.type)
    }

    @Test
    fun `CredentialResponse deserializes complete response correctly`() {
        val backendResponse = """
        {
            "credential": {
                "id": "urn:uuid:test-credential-id",
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "type": ["VerifiableCredential"],
                "issuer": "did:web:cachet.id",
                "issuanceDate": "2025-09-01T21:27:40Z",
                "credentialSubject": {
                    "id": "did:example:subject"
                }
            },
            "format": "jwt_vc"
        }
        """.trimIndent()

        val response = json.decodeFromString<CredentialResponse>(backendResponse)

        assertEquals("jwt_vc", response.format)
        assertEquals("urn:uuid:test-credential-id", response.credential.id)
        assertEquals("did:web:cachet.id", response.credential.issuer)
    }

    @Test
    fun `VerifiableCredential handles optional fields correctly`() {
        // Test with minimal required fields only
        val minimalResponse = """
        {
            "id": "urn:uuid:minimal-credential",
            "@context": ["https://www.w3.org/2018/credentials/v1"],
            "type": ["VerifiableCredential"],
            "issuer": "did:web:cachet.id",
            "issuanceDate": "2025-09-01T21:27:40Z",
            "credentialSubject": {
                "id": "did:example:subject"
            }
        }
        """.trimIndent()

        val credential = json.decodeFromString<VerifiableCredential>(minimalResponse)

        assertEquals("urn:uuid:minimal-credential", credential.id)
        assertEquals(null, credential.expirationDate)
        assertEquals(null, credential.credentialStatus)
    }

    @Test
    fun `VerifiableCredential date parsing utilities work correctly`() {
        val credential = VerifiableCredential(
            id = "urn:uuid:test",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential"),
            issuer = "did:web:cachet.id",
            issuanceDate = "2025-09-01T21:27:40Z",
            expirationDate = "2026-09-01T21:27:40Z",
            credentialSubject = mapOf("id" to JsonPrimitive("did:example:subject")),
            credentialStatus = null
        )

        // Test date parsing utilities
        val issuanceInstant = credential.getIssuanceInstant()
        assertNotNull(issuanceInstant)

        val isExpired = credential.isExpired()
        assertTrue(!isExpired) // Should not be expired as expiry is in 2026
    }

    @Test
    fun `VerifiableCredential handles malformed dates gracefully`() {
        val credential = VerifiableCredential(
            id = "urn:uuid:test",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential"),
            issuer = "did:web:cachet.id",
            issuanceDate = "invalid-date-format",
            expirationDate = "also-invalid",
            credentialSubject = mapOf("id" to JsonPrimitive("did:example:subject")),
            credentialStatus = null
        )

        // Should handle invalid dates gracefully
        val issuanceInstant = credential.getIssuanceInstant()
        assertEquals(null, issuanceInstant)

        val isExpired = credential.isExpired()
        assertTrue(!isExpired) // Should return false for invalid expiry date
    }

    @Test
    fun `Schema validation catches breaking changes`() {
        // Test that would fail if backend removes required fields
        val brokenResponse = """
        {
            "id": "urn:uuid:test",
            "context": ["https://www.w3.org/2018/credentials/v1"],
            "type": ["VerifiableCredential"],
            "issuer": "did:web:cachet.id"
        }
        """.trimIndent()

        // This should fail because issuanceDate and credentialSubject are required
        assertFails {
            json.decodeFromString<VerifiableCredential>(brokenResponse)
        }
    }

    @Test
    fun `Unknown fields in response are ignored gracefully`() {
        // Backend adds new optional fields
        val responseWithNewFields = """
        {
            "id": "urn:uuid:test",
            "@context": ["https://www.w3.org/2018/credentials/v1"],
            "type": ["VerifiableCredential"],
            "issuer": "did:web:cachet.id",
            "issuanceDate": "2025-09-01T21:27:40Z",
            "credentialSubject": {
                "id": "did:example:subject"
            },
            "newField": "this is a new field we don't know about",
            "anotherNewField": {
                "nested": "data"
            }
        }
        """.trimIndent()

        // Should deserialize successfully, ignoring unknown fields
        val credential = json.decodeFromString<VerifiableCredential>(responseWithNewFields)
        assertEquals("urn:uuid:test", credential.id)
    }
}