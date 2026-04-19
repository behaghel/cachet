package id.cachet.wallet.domain.transport

import id.cachet.wallet.domain.crypto.EphemeralKeyGenerator
import id.cachet.wallet.domain.crypto.EphemeralKeyPair

/**
 * Manages proximity verification sessions entirely on-device.
 * No backend calls — generates nonce and ephemeral key pair locally.
 */
class LocalSessionManager(
    private val keyGenerator: EphemeralKeyGenerator,
    private val nonceGenerator: NonceGenerator = SecureNonceGenerator()
) {
    /**
     * Create a new proximity session with a fresh nonce and ephemeral X25519 key pair.
     * The private key is held in memory only — never persisted.
     */
    fun createSession(params: SessionParams): ProximitySession {
        val nonce = nonceGenerator.generate()
        val keyPair = keyGenerator.generateX25519KeyPair()
        return ProximitySession(
            nonce = nonce,
            keyPair = keyPair,
            packId = params.packId,
            question = params.question,
            predicates = params.predicates
        )
    }
}

/** A proximity verification session with all parameters needed for the QR. */
data class ProximitySession(
    val nonce: String,
    val keyPair: EphemeralKeyPair,
    val packId: String,
    val question: String,
    val predicates: List<String>
)

/** Generates cryptographically secure nonces. */
interface NonceGenerator {
    /** Generate a 128-bit random nonce, base64url-encoded (22 chars). */
    fun generate(): String
}

/** Default nonce generator using expect/actual for platform-specific SecureRandom. */
expect class SecureNonceGenerator() : NonceGenerator {
    override fun generate(): String
}
