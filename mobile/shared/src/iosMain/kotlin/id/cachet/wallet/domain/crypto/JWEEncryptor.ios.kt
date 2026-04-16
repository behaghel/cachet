package id.cachet.wallet.domain.crypto

/**
 * iOS stub — JWE encryption not yet implemented for iOS.
 * Production would use CryptoKit ECDH-ES+A256KW / A256GCM.
 */
actual class JWEEncryptor actual constructor() {
    actual fun encrypt(plaintext: ByteArray, recipientPubKeyBase64URL: String): String {
        throw UnsupportedOperationException("JWE encryption not yet implemented for iOS")
    }
}
