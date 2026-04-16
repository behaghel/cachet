package id.cachet.wallet.domain.crypto

/**
 * iOS stub — JWS verification not yet implemented for iOS.
 * Production would use CryptoKit ES256 verification.
 */
actual class JWSVerifier actual constructor() {
    actual fun verify(jwsCompact: String, publicKeyJWK: String): String {
        throw UnsupportedOperationException("JWS verification not yet implemented for iOS")
    }
}
