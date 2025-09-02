package id.cachet.wallet.network

import id.cachet.wallet.domain.model.VerifiableCredential
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface OpenID4VCIClient {
    suspend fun requestToken(clientId: String, scope: String): TokenResponse
    suspend fun requestCredential(accessToken: String, format: String, types: List<String>): CredentialResponse
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
    val proof: Map<String, String>? = null
)

@Serializable
data class CredentialResponse(
    val credential: VerifiableCredential,
    val format: String
)

class OpenID4VCIException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KtorOpenID4VCIClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8090"
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
            
            val jsonString = json.encodeToString(TokenRequest.serializer(), request)
            println("DEBUG: Sending token request: grant_type=${request.grantType}, client_id=${request.clientId}, scope=${request.scope}")
            println("DEBUG: JSON payload: $jsonString")
            
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
        types: List<String>
    ): CredentialResponse {
        try {
            val request = CredentialRequest(
                format = format,
                types = types
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
}