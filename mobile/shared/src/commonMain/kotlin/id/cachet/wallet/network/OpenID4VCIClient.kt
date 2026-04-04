package id.cachet.wallet.network

import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.domain.model.VerifiableCredential
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

interface OpenID4VCIClient {
    suspend fun requestToken(clientId: String, scope: String, sessionId: String? = null): TokenResponse
    suspend fun requestCredential(accessToken: String, format: String, types: List<String>): CredentialResponse
    suspend fun requestSDJWTCredential(accessToken: String, types: List<String>, holderJWK: String): SDJWTCredentialResponse
}

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

@Serializable
data class SDJWTCredentialResponse(
    val credential: String, // raw SD-JWT string (issuerJWT~disc1~...~)
    val format: String
)

class OpenID4VCIException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KtorOpenID4VCIClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = AppConfig.baseUrl
) : OpenID4VCIClient {

    override suspend fun requestToken(clientId: String, scope: String, sessionId: String?): TokenResponse {
        try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/oauth/token",
                formParameters = parameters {
                    append("grant_type", "client_credentials")
                    append("client_id", clientId)
                    append("scope", scope)
                    if (sessionId != null) append("session_id", sessionId)
                }
            )

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
            val request = CredentialRequest(format = format, types = types)

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

    override suspend fun requestSDJWTCredential(
        accessToken: String,
        types: List<String>,
        holderJWK: String
    ): SDJWTCredentialResponse {
        try {
            val request = CredentialRequest(
                format = "vc+sd-jwt",
                types = types,
                proof = mapOf("jwk" to holderJWK)
            )

            val response: HttpResponse = httpClient.post("$baseUrl/credential") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(request)
            }

            if (response.status.isSuccess()) {
                return response.body<SDJWTCredentialResponse>()
            } else if (response.status == HttpStatusCode.Unauthorized) {
                throw OpenID4VCIException("Invalid access token")
            } else {
                throw OpenID4VCIException("SD-JWT credential request failed: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is OpenID4VCIException) throw e
            throw OpenID4VCIException("Network error during SD-JWT credential request", e)
        }
    }
}
