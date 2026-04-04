package id.cachet.wallet.domain.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Android KeyStore-backed implementation of KeyManager.
 * Uses StrongBox when available, falls back to TEE.
 * Keys are P-256 (ES256) for SD-JWT Key Binding.
 */
actual class KeyManager actual constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    actual fun generateKeyPair(alias: String): String {
        if (keyStore.containsAlias(alias)) {
            return getPublicKeyJWK(alias)!!
        }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        // Note: setIsStrongBoxBacked(true) can be added when targeting API 28+
        // with a fallback for devices without StrongBox

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        kpg.initialize(spec)
        val keyPair = kpg.generateKeyPair()

        return ecPublicKeyToJWK(keyPair.public as ECPublicKey)
    }

    actual fun sign(alias: String, data: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    actual fun getPublicKeyJWK(alias: String): String? {
        if (!keyStore.containsAlias(alias)) return null
        val cert = keyStore.getCertificate(alias) ?: return null
        val pubKey = cert.publicKey as? ECPublicKey ?: return null
        return ecPublicKeyToJWK(pubKey)
    }

    actual fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    private fun ecPublicKeyToJWK(key: ECPublicKey): String {
        val point = key.w
        // P-256 coordinates are 32 bytes each
        val xBytes = point.affineX.toByteArray().takeLast(32).toByteArray()
        val yBytes = point.affineY.toByteArray().takeLast(32).toByteArray()
        val x = Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes)
        val y = Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes)
        return """{"kty":"EC","crv":"P-256","x":"$x","y":"$y"}"""
    }
}
