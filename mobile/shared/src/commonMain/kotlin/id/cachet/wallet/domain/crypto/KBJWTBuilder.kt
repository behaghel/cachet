package id.cachet.wallet.domain.crypto

import id.cachet.wallet.domain.model.sha256Hash
import kotlin.time.Clock

/**
 * Builds Key Binding JWTs (KB-JWT) for SD-JWT presentations.
 *
 * A KB-JWT proves the presenter holds the private key bound to the credential
 * via the cnf claim. It contains:
 * - nonce: verifier-supplied challenge (replay protection)
 * - aud: verifier's DID (audience binding)
 * - iat: current timestamp
 * - sd_hash: hash of the SD-JWT + disclosures being presented
 */
object KBJWTBuilder {

    /**
     * Build a KB-JWT for an SD-JWT presentation.
     *
     * @param nonce Verifier-supplied nonce for replay protection
     * @param audience Verifier's DID for audience binding
     * @param sdJwtWithDisclosures The SD-JWT content being presented (issuerJWT~disc1~disc2~...~)
     * @param keyManager Platform key manager for signing
     * @param keyAlias Alias of the holder's private key in the key store
     * @return The signed KB-JWT string (header.payload.signature)
     */
    fun build(
        nonce: String,
        audience: String,
        sdJwtWithDisclosures: String,
        keyManager: KeyManager,
        keyAlias: String
    ): String {
        val header = """{"alg":"ES256","typ":"kb+jwt"}"""
        val sdHash = computeSDHash(sdJwtWithDisclosures)
        val iat = Clock.System.now().epochSeconds
        val payload = """{"nonce":"$nonce","aud":"$audience","iat":$iat,"sd_hash":"$sdHash"}"""

        val headerEncoded = Base64Url.encode(header.encodeToByteArray())
        val payloadEncoded = Base64Url.encode(payload.encodeToByteArray())
        val signingInput = "$headerEncoded.$payloadEncoded"

        val signatureBytes = keyManager.sign(keyAlias, signingInput.encodeToByteArray())
        val signatureEncoded = Base64Url.encode(signatureBytes)

        return "$signingInput.$signatureEncoded"
    }

    /**
     * Build a proof JWT for OpenID4VCI credential issuance (T15 mitigation).
     * Proves the holder controls the key and binds the proof to a c_nonce.
     *
     * @param nonce c_nonce from the issuer's /nonce endpoint
     * @param audience Credential issuer identifier (e.g., "https://cachet.id")
     * @param keyManager Platform key manager for signing
     * @param keyAlias Alias of the holder's private key
     * @return Signed proof JWT (header.payload.signature)
     */
    fun buildProofJWT(
        nonce: String,
        audience: String,
        keyManager: KeyManager,
        keyAlias: String
    ): String {
        val header = """{"alg":"ES256","typ":"openid4vci-proof+jwt"}"""
        val iat = Clock.System.now().epochSeconds
        val payload = """{"nonce":"$nonce","aud":"$audience","iat":$iat}"""

        val headerEncoded = Base64Url.encode(header.encodeToByteArray())
        val payloadEncoded = Base64Url.encode(payload.encodeToByteArray())
        val signingInput = "$headerEncoded.$payloadEncoded"

        val signatureBytes = keyManager.sign(keyAlias, signingInput.encodeToByteArray())
        val signatureEncoded = Base64Url.encode(signatureBytes)

        return "$signingInput.$signatureEncoded"
    }

    /**
     * Compute the sd_hash: base64url(sha256(sdJwtWithDisclosures))
     */
    fun computeSDHash(sdJwtContent: String): String {
        // sha256Hash returns hex string, we need the raw bytes for base64url
        val hexHash = sha256Hash(sdJwtContent)
        val hashBytes = hexHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return Base64Url.encode(hashBytes)
    }
}
