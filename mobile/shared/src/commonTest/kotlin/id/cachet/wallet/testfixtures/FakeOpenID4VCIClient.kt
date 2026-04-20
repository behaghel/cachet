package id.cachet.wallet.testfixtures

import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.network.NonceResponse
import id.cachet.wallet.network.OpenID4VCIClient
import id.cachet.wallet.network.SDJWTCredentialResponse
import id.cachet.wallet.network.TokenResponse

class FakeOpenID4VCIClient(
    var tokenResponse: TokenResponse = TokenResponse(
        access_token = "fake-token",
        token_type = "Bearer",
        expires_in = 3600,
        scope = "openid"
    ),
    var credentialResponse: CredentialResponse? = null,
    var sdJwtCredentialResponse: SDJWTCredentialResponse? = null,
    var tokenError: Throwable? = null,
    var credentialError: Throwable? = null
) : OpenID4VCIClient {

    override suspend fun requestToken(clientId: String, scope: String, sessionId: String?): TokenResponse {
        tokenError?.let { throw it }
        return tokenResponse
    }

    override suspend fun requestNonce(): NonceResponse {
        return NonceResponse(c_nonce = "fake-nonce", c_nonce_expires_in = 300)
    }

    override suspend fun requestCredential(
        accessToken: String,
        format: String,
        types: List<String>
    ): CredentialResponse {
        credentialError?.let { throw it }
        return credentialResponse ?: error("FakeOpenID4VCIClient: credentialResponse not configured")
    }

    override suspend fun requestSDJWTCredential(
        accessToken: String,
        types: List<String>,
        holderJWK: String,
        proofJWT: String?
    ): SDJWTCredentialResponse {
        return sdJwtCredentialResponse ?: error("FakeOpenID4VCIClient: sdJwtCredentialResponse not configured")
    }
}
