package id.cachet.wallet.network

import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.model.VaultArtifact
import id.cachet.wallet.domain.model.VaultPredicate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json

interface OpenID4VCIClient {
    suspend fun requestToken(clientId: String, scope: String): TokenResponse
    suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>,
        sessionId: String
    ): CredentialResponse
    suspend fun getVerificationStatus(sessionId: String): VerificationStatusResponse
}

@Serializable
data class TokenRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("scope") val scope: String
)

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val scope: String
) {
    val accessToken get() = access_token
    val tokenType get() = token_type
    val expiresIn get() = expires_in
}

@Serializable
data class CredentialRequest(
    val format: String,
    val types: List<String>,
    @SerialName("sessionId") val sessionId: String,
    val proof: Map<String, String>? = null
)

@Serializable
data class CredentialResponse(
    val credential: VerifiableCredential,
    val format: String,
    val vaultArtifacts: List<VaultArtifactDTO>? = null,
    val vaultPredicates: List<VaultPredicateDTO>? = null
)

@Serializable
data class VerificationStatusResponse(
    @SerialName("sessionId") val sessionId: String,
    val status: String,
    val action: String? = null,
    val code: Int? = null
)

class OpenID4VCIException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class VaultArtifactDTO(
    val id: String,
    val type: String,
    val source: String,
    val payload: JsonElement,
    val createdAt: Long
)

@Serializable
data class VaultPredicateDTO(
    val id: String,
    val key: String,
    val value: String,
    val proofType: String? = null,
    val issuedAt: Long,
    val expiresAt: Long? = null,
    val artifact: VaultArtifactDTO? = null
)

class KtorOpenID4VCIClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : OpenID4VCIClient {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun requestToken(clientId: String, scope: String): TokenResponse {
        try {
            val request = TokenRequest(
                grantType = "client_credentials",
                clientId = clientId,
                scope = scope
            )
            
            val response: HttpResponse = httpClient.post("$baseUrl/oauth/token") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                return response.body<TokenResponse>()
            } else {
                throw OpenID4VCIException("Token request failed: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is OpenID4VCIException) throw e
            throw OpenID4VCIException("Network error during token request", e)
        }
    }
    
    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>,
        sessionId: String
    ): CredentialResponse {
        try {
            val request = CredentialRequest(
                format = format,
                types = types,
                sessionId = sessionId
            )
            
            val response: HttpResponse = httpClient.post("$baseUrl/credential") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                return response.body<CredentialResponse>()
            } else if (response.status == HttpStatusCode.Unauthorized) {
                throw OpenID4VCIException("Invalid access token")
            } else {
                throw OpenID4VCIException("Credential request failed: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is OpenID4VCIException) throw e
            throw OpenID4VCIException("Network error during credential request", e)
        }
    }

    override suspend fun getVerificationStatus(sessionId: String): VerificationStatusResponse {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/sessions/veriff/$sessionId") {
                accept(ContentType.Application.Json)
            }

            if (response.status.isSuccess()) {
                return response.body()
            }

            throw OpenID4VCIException("Status request failed: ${response.status}")
        } catch (e: Exception) {
            if (e is OpenID4VCIException) throw e
            throw OpenID4VCIException("Network error during status request", e)
        }
    }
}
fun VaultArtifactDTO.toVaultArtifact(): VaultArtifact = VaultArtifact(
    id = id,
    type = type,
    source = source,
    payload = payload,
    createdAt = kotlinx.datetime.Instant.fromEpochSeconds(createdAt)
)

fun VaultPredicateDTO.toVaultPredicate(): VaultPredicate = VaultPredicate(
    id = id,
    key = key,
    value = value,
    proofType = proofType,
    issuedAt = kotlinx.datetime.Instant.fromEpochSeconds(issuedAt),
    expiresAt = expiresAt?.let { kotlinx.datetime.Instant.fromEpochSeconds(it) },
    artifact = artifact?.toVaultArtifact()
)
