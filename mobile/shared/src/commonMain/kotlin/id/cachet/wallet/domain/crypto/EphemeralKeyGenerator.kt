package id.cachet.wallet.domain.crypto

/**
 * Generates ephemeral X25519 key pairs for proximity verification sessions.
 * The private key is held in memory only (never persisted to disk or keystore).
 */
interface EphemeralKeyGenerator {
    /**
     * Generate a new X25519 key pair.
     * @return Key pair with base64url-encoded public and private keys (each 32 bytes raw)
     */
    fun generateX25519KeyPair(): EphemeralKeyPair
}

/**
 * An ephemeral X25519 key pair for a single proximity verification session.
 * The private key must not be persisted — it lives only in memory for the
 * duration of the session.
 */
data class EphemeralKeyPair(
    /** Base64url-encoded X25519 public key (32 bytes raw) */
    val publicKeyBase64URL: String,
    /** Base64url-encoded X25519 private key (32 bytes raw) */
    val privateKeyBase64URL: String
)
