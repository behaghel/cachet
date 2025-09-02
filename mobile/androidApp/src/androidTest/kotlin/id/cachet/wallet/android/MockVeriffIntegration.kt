package id.cachet.wallet.android

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.TokenResponse
import id.cachet.wallet.network.CredentialResponse
import kotlinx.datetime.Clock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Mock implementation that simulates the full Veriff verification flow
 * including OAuth2 token exchange and credential issuance.
 */
class MockVeriffIntegration : OpenID4VCIClient {
    
    var simulateSuccess = true
    var simulateNetworkDelay = true
    var customErrorMessage: String? = null
    private var customCredentialData: Map<String, JsonElement>? = null
    
    // Simulate different verification scenarios
    enum class VerificationScenario {
        SUCCESS,
        NETWORK_ERROR,
        INVALID_TOKEN,
        VERIFICATION_FAILED,
        EXPIRED_SESSION
    }
    
    var scenario: VerificationScenario = VerificationScenario.SUCCESS
    
    override suspend fun requestToken(clientId: String, scope: String): TokenResponse {
        if (simulateNetworkDelay) {
            delay(1500) // Simulate network delay
        }
        
        when (scenario) {
            VerificationScenario.NETWORK_ERROR -> {
                throw Exception("Network connection failed - could not reach Veriff servers")
            }
            VerificationScenario.EXPIRED_SESSION -> {
                throw Exception("Verification session expired")
            }
            else -> {
                // Simulate successful OAuth2 token response
                return TokenResponse(
                    access_token = "mock-veriff-token-${System.currentTimeMillis()}",
                    token_type = "Bearer",
                    expires_in = 3600,
                    scope = scope
                )
            }
        }
    }
    
    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>
    ): CredentialResponse {
        if (simulateNetworkDelay) {
            delay(2000) // Simulate longer processing time for credential issuance
        }
        
        when (scenario) {
            VerificationScenario.INVALID_TOKEN -> {
                throw Exception("Invalid or expired access token")
            }
            VerificationScenario.VERIFICATION_FAILED -> {
                throw Exception("Identity verification failed - please try again with valid documents")
            }
            VerificationScenario.NETWORK_ERROR -> {
                throw Exception("Network error during credential issuance")
            }
            else -> {
                // Create mock credential based on successful Veriff verification
                val credential = createMockVerifiedCredential()
                
                return CredentialResponse(
                    credential = credential,
                    format = format
                )
            }
        }
    }
    
    private fun createMockVerifiedCredential(): VerifiableCredential {
        val now = Clock.System.now()
        
        // Use custom data if provided, otherwise use default mock data
        val credentialSubject = customCredentialData ?: mapOf(
            "id" to JsonPrimitive("did:example:user123456"),
            "name" to JsonPrimitive("John Doe"),
            "dateOfBirth" to JsonPrimitive("1990-01-15"),
            "nationality" to JsonPrimitive("US"),
            "documentType" to JsonPrimitive("passport"),
            "documentNumber" to JsonPrimitive("P123456789"),
            "verificationLevel" to JsonPrimitive("full"),
            "verifiedAt" to JsonPrimitive(now.toString()),
            "veriffSessionId" to JsonPrimitive("veriff-session-${System.currentTimeMillis()}"),
            "biometricsVerified" to JsonPrimitive(true),
            "documentVerified" to JsonPrimitive(true),
            "faceMatch" to JsonPrimitive(true),
            "verificationScore" to JsonPrimitive(0.98)
        )
        
        return VerifiableCredential(
            id = "urn:credential:veriff-verification:${System.currentTimeMillis()}",
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://cachet.id/contexts/identity-verification/v1"
            ),
            type = listOf(
                "VerifiableCredential",
                "IdentityVerificationCredential"
            ),
            issuer = "did:web:cachet.id",
            issuanceDate = now.toString(),
            expirationDate = null, // Identity verification doesn't typically expire
            credentialSubject = credentialSubject,
            credentialStatus = CredentialStatus(
                id = "https://cachet.id/status/v1#${System.currentTimeMillis()}",
                type = "StatusList2021Entry"
            )
        )
    }
    
    // Helper methods for testing different scenarios
    
    fun simulateSuccessfulVerification() {
        scenario = VerificationScenario.SUCCESS
        simulateSuccess = true
    }
    
    fun simulateNetworkError() {
        scenario = VerificationScenario.NETWORK_ERROR
        simulateSuccess = false
    }
    
    fun simulateInvalidToken() {
        scenario = VerificationScenario.INVALID_TOKEN
        simulateSuccess = false
    }
    
    fun simulateVerificationFailure() {
        scenario = VerificationScenario.VERIFICATION_FAILED
        simulateSuccess = false
    }
    
    fun simulateExpiredSession() {
        scenario = VerificationScenario.EXPIRED_SESSION
        simulateSuccess = false
    }
    
    fun setCustomCredentialData(data: Map<String, JsonElement>) {
        customCredentialData = data
    }
    
    fun setNetworkDelay(enabled: Boolean) {
        simulateNetworkDelay = enabled
    }
}