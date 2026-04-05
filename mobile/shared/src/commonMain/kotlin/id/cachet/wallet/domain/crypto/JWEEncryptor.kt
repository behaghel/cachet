package id.cachet.wallet.domain.crypto

/**
 * Encrypts plaintext to a JWE Compact Serialization string using the
 * recipient's X25519 public key. Algorithm: ECDH-ES+A256KW / A256GCM.
 */
expect class JWEEncryptor() {
    /**
     * @param plaintext The data to encrypt (SD-JWT+KB-JWT presentation)
     * @param recipientPubKeyBase64URL The verifier's ephemeral X25519 public key (base64url, 32 bytes)
     * @return JWE Compact Serialization (header.encryptedKey.iv.ciphertext.tag)
     */
    fun encrypt(plaintext: ByteArray, recipientPubKeyBase64URL: String): String
}
