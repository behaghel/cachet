package id.cachet.wallet.domain.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android-specific implementations of cryptographic functions for consent receipts
 */

/**
 * Compute SHA-256 hash using Android's MessageDigest
 */
actual fun sha256Hash(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

/**
 * Generate EdDSA signature using Android cryptographic libraries
 * This is a placeholder implementation - production would use proper EdDSA
 */
actual fun signConsentReceipt(canonicalContent: String, privateKey: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    val keySpec = SecretKeySpec(privateKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    mac.init(keySpec)
    val signatureBytes = mac.doFinal(canonicalContent.toByteArray(StandardCharsets.UTF_8))
    return signatureBytes.joinToString(separator = "") { byte ->
        ((byte.toInt() and 0xFF).toString(16)).padStart(2, '0')
    }
}

actual fun verifyConsentReceiptSignature(
    canonicalContent: String,
    signature: String,
    publicKey: String
): Boolean {
    val expected = signConsentReceipt(canonicalContent, publicKey)
    return expected.equals(signature, ignoreCase = true)
}

private val secureRandom by lazy { SecureRandom() }

actual fun secureRandomBytes(length: Int): ByteArray {
    require(length > 0) { "Byte length must be positive" }
    val bytes = ByteArray(length)
    secureRandom.nextBytes(bytes)
    return bytes
}
