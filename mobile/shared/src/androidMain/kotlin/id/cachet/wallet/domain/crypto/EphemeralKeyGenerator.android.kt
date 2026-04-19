package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec

/**
 * Android implementation using Bouncy Castle for X25519 key generation.
 */
class AndroidEphemeralKeyGenerator : EphemeralKeyGenerator {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun generateX25519KeyPair(): EphemeralKeyPair {
        val kpg = KeyPairGenerator.getInstance("X25519", "BC")
        kpg.initialize(NamedParameterSpec.X25519)
        val kp = kpg.generateKeyPair()

        val pubKey = kp.public as XECPublicKey
        val privKey = kp.private as XECPrivateKey

        val pubBytes = x25519PublicKeyToRawBytes(pubKey)
        val privBytes = privKey.scalar.orElseThrow {
            IllegalStateException("Cannot extract X25519 private key scalar")
        }

        return EphemeralKeyPair(
            publicKeyBase64URL = Base64URL.encode(pubBytes).toString(),
            privateKeyBase64URL = Base64URL.encode(privBytes).toString()
        )
    }

    /**
     * Extract the raw 32-byte X25519 public key from an XECPublicKey.
     * The u-coordinate is returned in little-endian byte order (RFC 7748).
     */
    private fun x25519PublicKeyToRawBytes(pubKey: XECPublicKey): ByteArray {
        val u = pubKey.u
        val uBytes = u.toByteArray()

        // BigInteger is big-endian and may include a leading zero byte for sign.
        // X25519 public keys are 32 bytes, little-endian.
        val raw = ByteArray(32)
        val offset = if (uBytes.size > 32) uBytes.size - 32 else 0
        val len = minOf(uBytes.size, 32)
        System.arraycopy(uBytes, offset, raw, 32 - len, len)

        // Reverse to little-endian (RFC 7748 representation)
        raw.reverse()
        return raw
    }
}
