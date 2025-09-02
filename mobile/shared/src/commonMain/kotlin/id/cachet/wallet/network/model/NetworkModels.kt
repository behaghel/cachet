package id.cachet.wallet.network.model

import id.cachet.wallet.domain.model.VerifiableCredential
import kotlinx.serialization.Serializable

@Serializable
data class TokenRequest(
    val grant_type: String = "client_credentials",
    val client_id: String,
    val scope: String
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