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
     * Checks for `oauth-authz-req+jwt` typ header (Request Object verification).
     *
     * @param jwsCompact The JWS string (header.payload.signature)
     * @param publicKeyJWK The verifier's public key as a JWK JSON string
     * @return The verified payload as a JSON string
     * @throws SecurityException if the signature is invalid or the token is expired
     */
    fun verify(jwsCompact: String, publicKeyJWK: String): String

    /**
     * General-purpose JWS verification with configurable typ check.
     * Used by the local verifier for issuer JWTs (typ=vc+sd-jwt) and KB-JWTs (typ=kb+jwt).
     *
     * @param jwsCompact The JWS string (header.payload.signature)
     * @param publicKeyJWK The signer's public key as a JWK JSON string
     * @param expectedTyp Expected typ header value (null to skip typ check)
     * @return The verified payload as a JSON string
     * @throws SecurityException if the signature is invalid
     */
    fun verifyJWS(jwsCompact: String, publicKeyJWK: String, expectedTyp: String?): String
}
