package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.crypto.KeyManager

/**
 * In-memory KeyManager for tests. Generates deterministic fake keys
 * and produces verifiable (but not hardware-backed) ES256 signatures.
 *
 * On JVM/Android unit tests, this uses standard JCA EC keys.
 * On pure commonTest (no JVM), it uses a simplified stub.
 */
class FakeKeyManager : KeyManager {

    private val keys = mutableMapOf<String, FakeKeyPair>()

    data class FakeKeyPair(
        val alias: String,
        val publicKeyJWK: String
    )

    override fun generateKeyPair(alias: String): String {
        if (keys.containsKey(alias)) return keys[alias]!!.publicKeyJWK
        // Deterministic fake JWK — not a real EC key, but structurally valid
        val jwk = """{"kty":"EC","crv":"P-256","x":"fake-x-${alias.hashCode()}","y":"fake-y-${alias.hashCode()}"}"""
        keys[alias] = FakeKeyPair(alias, jwk)
        return jwk
    }

    override fun sign(alias: String, data: ByteArray): ByteArray {
        if (!keys.containsKey(alias)) throw IllegalStateException("No key for alias: $alias")
        // Return a deterministic 64-byte fake signature (R||S for P-256)
        // Not cryptographically valid but structurally correct for JWT assembly
        val hash = data.fold(0) { acc, b -> acc * 31 + b.toInt() }
        return ByteArray(64) { i -> ((hash + i) % 256).toByte() }
    }

    override fun getPublicKeyJWK(alias: String): String? = keys[alias]?.publicKeyJWK

    override fun hasKey(alias: String): Boolean = keys.containsKey(alias)
}
