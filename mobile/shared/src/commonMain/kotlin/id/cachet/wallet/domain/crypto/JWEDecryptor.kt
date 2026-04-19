package id.cachet.wallet.domain.crypto

/**
 * Decrypts a JWE Compact Serialization string using the recipient's X25519
 * private key. Algorithm: ECDH-ES+A256KW / A256GCM.
 *
 * Used by the verifier side in proximity mode to decrypt the holder's
 * encrypted VP after scanning the response QR.
 */
expect class JWEDecryptor() {
    /**
     * @param jweCompact JWE Compact Serialization (header.encryptedKey.iv.ciphertext.tag)
     * @param recipientPrivKeyBase64URL The verifier's ephemeral X25519 private key (base64url, 32 bytes)
     * @param recipientPubKeyBase64URL The verifier's ephemeral X25519 public key (base64url, 32 bytes)
     * @return Decrypted plaintext bytes
     * @throws Exception if decryption fails (wrong key, tampered ciphertext, etc.)
     */
    fun decrypt(jweCompact: String, recipientPrivKeyBase64URL: String, recipientPubKeyBase64URL: String): ByteArray
}
