package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.OpenID4VCIClient
import kotlinx.datetime.Clock
import kotlin.random.Random

class IssuanceUseCase(
    private val credentialRepository: CredentialRepository,
    private val openID4VCIClient: OpenID4VCIClient
) {
    
    private fun generateUuid(): String {
        val random = Random.Default
        val uuid = "${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}"
        return uuid
    }
    
    suspend fun requestCredential(
        clientId: String,
        credentialTypes: List<String>,
        format: String = "jwt_vc"
    ): Result<StoredCredential> {
        return try {
            // Step 1: Request OAuth2 token
            val tokenResponse = openID4VCIClient.requestToken(
                clientId = clientId,
                scope = "credential_issuance"
            )
            
            // Step 2: Request credential using the token
            val credentialResponse = openID4VCIClient.requestCredential(
                accessToken = tokenResponse.accessToken,
                format = format,
                types = credentialTypes
            )
            
            // Step 3: Create stored credential with local ID
            val storedCredential = StoredCredential(
                localId = generateUuid(),
                credential = credentialResponse.credential,
                rawJwt = null, // TODO: Extract JWT from response if format is jwt_vc
                createdAt = Clock.System.now(),
                isRevoked = false
            )
            
            // Step 4: Store credential in local repository
            credentialRepository.storeCredential(storedCredential)
            
            Result.success(storedCredential)
        } catch (e: Exception) {
            Result.failure(IssuanceException("Failed to issue credential: ${e.message}", e))
        }
    }
    
    suspend fun getStoredCredentials(): Result<List<StoredCredential>> {
        return try {
            val credentials = credentialRepository.getAllCredentials()
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(IssuanceException("Failed to retrieve credentials: ${e.message}", e))
        }
    }
    
    suspend fun getCredentialsByIssuer(issuer: String): Result<List<StoredCredential>> {
        return try {
            val credentials = credentialRepository.getCredentialsByIssuer(issuer)
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(IssuanceException("Failed to retrieve credentials by issuer: ${e.message}", e))
        }
    }
    
    suspend fun revokeCredential(localId: String): Result<Unit> {
        return try {
            credentialRepository.markCredentialRevoked(localId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IssuanceException("Failed to revoke credential: ${e.message}", e))
        }
    }
}

class IssuanceException(message: String, cause: Throwable? = null) : Exception(message, cause)