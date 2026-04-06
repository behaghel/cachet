package id.cachet.wallet.domain.crypto

/**
 * Verifies JWS (JSON Web Signature) Request Objects.
 * Returns the verified payload as a JSON string if the signature is valid.
 *
 * Algorithm: ES256. Header type: oauth-authz-req+jwt.
 */
expect class JWSVerifier() {
    /**
     * Verify a JWS compact serialization and extract the payload.
     *
     * @param jwsCompact The JWS string (header.payload.signature)
     * @param publicKeyJWK The verifier's public key as a JWK JSON string
     * @return The verified payload as a JSON string
     * @throws SecurityException if the signature is invalid or the token is expired
     */
    fun verify(jwsCompact: String, publicKeyJWK: String): String
}
