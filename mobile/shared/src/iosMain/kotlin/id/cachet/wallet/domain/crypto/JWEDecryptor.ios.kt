package id.cachet.wallet.domain.crypto

/**
 * iOS stub — JWE decryption not yet implemented for iOS.
 * Production would use CryptoKit ECDH-ES+A256KW / A256GCM.
 */
actual class JWEDecryptor actual constructor() {
    actual fun decrypt(
        jweCompact: String,
        recipientPrivKeyBase64URL: String,
        recipientPubKeyBase64URL: String
    ): ByteArray {
        throw UnsupportedOperationException("JWE decryption not yet implemented for iOS")
    }
}
