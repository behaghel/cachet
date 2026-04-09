package id.cachet.wallet.domain.crypto

/**
 * Platform-agnostic key management interface for holder key operations.
 * Keys are hardware-backed on Android (StrongBox/TEE) and Secure Enclave on iOS.
 *
 * Production: [AndroidKeyStoreKeyManager] (androidMain)
 * Tests: [FakeKeyManager] (commonTest testfixtures)
 */
interface KeyManager {
    /**
     * Generate a P-256 (ES256) key pair and return the public key as a JWK JSON string.
     * The private key is stored in hardware-backed secure storage under the given alias.
     * If a key already exists for the alias, returns the existing public key.
     */
    fun generateKeyPair(alias: String): String

    /**
     * Sign data with the private key stored under the given alias.
     * Uses SHA256withECDSA (ES256).
     * Returns the raw signature bytes (R||S, 64 bytes for P-256).
     */
    fun sign(alias: String, data: ByteArray): ByteArray

    /**
     * Returns the public key JWK JSON string for the given alias.
     * Returns null if no key exists for the alias.
     */
    fun getPublicKeyJWK(alias: String): String?

    /**
     * Check whether a key exists for the given alias.
     */
    fun hasKey(alias: String): Boolean
}
