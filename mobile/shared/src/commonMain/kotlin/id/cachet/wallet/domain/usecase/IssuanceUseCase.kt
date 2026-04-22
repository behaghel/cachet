package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.crypto.KeyManager
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.sync.IssuanceQueue
import id.cachet.wallet.domain.sync.PendingIssuanceState
import id.cachet.wallet.network.OpenID4VCIClient
import kotlin.time.Clock
import kotlin.random.Random

class IssuanceUseCase(
    private val credentialRepository: CredentialRepository,
    private val openID4VCIClient: OpenID4VCIClient,
    private val keyManager: KeyManager? = null,
    private val issuanceQueue: IssuanceQueue? = null
) {
    
    private fun generateUuid(): String {
        val random = Random.Default
        val uuid = "${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}-${random.nextInt().toString(16)}"
        return uuid
    }
    
    suspend fun requestCredential(
        clientId: String,
        credentialTypes: List<String>,
        format: String = "jwt_vc",
        sessionId: String? = null
    ): Result<StoredCredential> {
        return try {
            // Step 1: Request OAuth2 token (with optional Veriff session binding)
            val tokenResponse = openID4VCIClient.requestToken(
                clientId = clientId,
                scope = "credential_issuance",
                sessionId = sessionId
            )

            // Step 2: Request credential using the token
            // If this fails, persist partial state for retry on reconnect
            try {
                val credentialResponse = openID4VCIClient.requestCredential(
                    accessToken = tokenResponse.accessToken,
                    format = format,
                    types = credentialTypes
                )

                val storedCredential = StoredCredential(
                    localId = generateUuid(),
                    credential = credentialResponse.credential,
                    rawJwt = null,
                    createdAt = Clock.System.now(),
                    isRevoked = false
                )

                credentialRepository.storeCredential(storedCredential)
                Result.success(storedCredential)
            } catch (e: Exception) {
                val expiresAt = Clock.System.now().toEpochMilliseconds() + (tokenResponse.expiresIn * 1000L)
                issuanceQueue?.enqueue(
                    PendingIssuanceState(
                        id = generateUuid(),
                        clientId = clientId,
                        credentialTypes = credentialTypes,
                        format = format,
                        sessionId = sessionId,
                        accessToken = tokenResponse.accessToken,
                        tokenExpiresAt = expiresAt,
                        keyAlias = null,
                        holderJwk = null
                    )
                )
                Result.failure(IssuanceException("Credential fetch failed, queued for retry: ${e.message}", e))
            }
        } catch (e: Exception) {
            // Token request itself failed — nothing to queue
            Result.failure(IssuanceException("Failed to issue credential: ${e.message}", e))
        }
    }
    
    /**
     * Request an SD-JWT credential with holder binding.
     * Generates a hardware-backed key pair, sends the public key JWK to the issuer,
     * and stores the SD-JWT with the key alias for future KB-JWT signing.
     */
    suspend fun requestSDJWTCredential(
        clientId: String,
        credentialTypes: List<String>,
        sessionId: String? = null
    ): Result<StoredCredential> {
        val km = keyManager
            ?: return Result.failure(IssuanceException("KeyManager not available — cannot issue SD-JWT credentials"))

        return try {
            // Step 1: Generate holder key pair (hardware-backed)
            val keyAlias = "cachet-holder-${generateUuid()}"
            val holderJWK = km.generateKeyPair(keyAlias)

            // Step 2: Request OAuth2 token
            val tokenResponse = openID4VCIClient.requestToken(
                clientId = clientId,
                scope = "credential_issuance",
                sessionId = sessionId
            )

            // Step 3: Fetch credential — if this fails, persist partial state for retry
            try {
                // Fetch c_nonce for proof replay prevention (T15)
                val cNonce = try {
                    openID4VCIClient.requestNonce().cNonce
                } catch (_: Exception) {
                    null // graceful fallback if issuer doesn't support nonce yet
                }

                val proofJWT = if (cNonce != null) {
                    id.cachet.wallet.domain.crypto.KBJWTBuilder.buildProofJWT(
                        nonce = cNonce,
                        audience = id.cachet.wallet.config.AppConfig.baseUrl,
                        keyManager = km,
                        keyAlias = keyAlias
                    )
                } else null

                val credentialResponse = openID4VCIClient.requestSDJWTCredential(
                    accessToken = tokenResponse.accessToken,
                    types = credentialTypes,
                    holderJWK = holderJWK,
                    proofJWT = proofJWT
                )

                val parsed = id.cachet.wallet.domain.crypto.SDJWTParser.parse(credentialResponse.credential)
                val displayCredential = buildDisplayCredentialFromSDJWT(parsed, credentialTypes)

                val storedCredential = StoredCredential(
                    localId = generateUuid(),
                    credential = displayCredential,
                    rawSdJwt = credentialResponse.credential,
                    keyAlias = keyAlias,
                    createdAt = Clock.System.now(),
                    isRevoked = false
                )

                credentialRepository.storeCredential(storedCredential)
                Result.success(storedCredential)
            } catch (e: Exception) {
                // Credential fetch failed after token was obtained — persist for retry
                val expiresAt = Clock.System.now().toEpochMilliseconds() + (tokenResponse.expiresIn * 1000L)
                issuanceQueue?.enqueue(
                    PendingIssuanceState(
                        id = generateUuid(),
                        clientId = clientId,
                        credentialTypes = credentialTypes,
                        format = "vc+sd-jwt",
                        sessionId = sessionId,
                        accessToken = tokenResponse.accessToken,
                        tokenExpiresAt = expiresAt,
                        keyAlias = keyAlias,
                        holderJwk = holderJWK
                    )
                )
                Result.failure(IssuanceException("Credential fetch failed, queued for retry: ${e.message}", e))
            }
        } catch (e: Exception) {
            // Token request itself failed — nothing to queue
            Result.failure(IssuanceException("Failed to issue SD-JWT credential: ${e.message}", e))
        }
    }

    private fun buildDisplayCredentialFromSDJWT(
        parsed: id.cachet.wallet.domain.crypto.SDJWTParser.ParsedSDJWT,
        types: List<String>
    ): id.cachet.wallet.domain.model.VerifiableCredential {
        // Extract display values from disclosed claims for the UI
        val ageClaim = parsed.claims["age"]
        val age = ageClaim?.toString()?.toDoubleOrNull()?.toInt()
        val nationality = parsed.claims["nationality"]?.toString()?.removeSurrounding("\"")
        val documentType = parsed.claims["documentType"]?.toString()?.removeSurrounding("\"")

        return id.cachet.wallet.domain.model.VerifiableCredential(
            id = "urn:sd-jwt:${generateUuid()}",
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = types,
            issuer = "did:veriff:production",
            issuanceDate = Clock.System.now().toString(),
            credentialSubject = id.cachet.wallet.domain.model.CredentialSubject(
                id = "did:example:holder",
                verified = true,
                personalData = id.cachet.wallet.domain.model.PersonalData(
                    age = age,
                    nationality = nationality,
                    documentType = documentType
                )
            )
        )
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