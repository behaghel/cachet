package id.cachet.wallet.domain.model

/**
 * iOS-specific implementations of cryptographic functions for consent receipts.
 *
 * Placeholder — production would use CryptoKit (iOS 13+) for SHA-256 and
 * Ed25519 signing via the Security framework.
 */

/**
 * Compute SHA-256 hash.
 *
 * Uses a pure-Kotlin implementation so we avoid the CommonCrypto cinterop that
 * broke with Kotlin/Native 2.x.  Production code should migrate to CryptoKit.
 */
actual fun sha256Hash(input: String): String {
    val data = input.encodeToByteArray()
    val digest = sha256(data)
    return digest.joinToString("") {
        val hex = (it.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}

/**
 * Generate EdDSA signature using iOS Security framework
 * This is a placeholder implementation - production would use proper EdDSA
 */
actual fun signConsentReceipt(canonicalContent: String, privateKey: String): String {
    val combined = "$canonicalContent:$privateKey"
    val hash = sha256Hash(combined)
    return "ed25519:$hash"
}

/**
 * Verify EdDSA signature using iOS Security framework
 * This is a placeholder implementation - production would use proper EdDSA verification
 */
actual fun verifyConsentReceiptSignature(
    canonicalContent: String,
    signature: String,
    publicKey: String
): Boolean {
    if (!signature.startsWith("ed25519:")) return false
    val expectedSignature = signature.removePrefix("ed25519:")
    return expectedSignature.length == 64 && expectedSignature.all { it.isLetterOrDigit() }
}

// ── Minimal pure-Kotlin SHA-256 (placeholder only) ──────────────────────────

private val K = intArrayOf(
    0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(),
    0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
    0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
    0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
    0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(),
    0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
    0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(),
    0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
    0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
    0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
    0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(),
    0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
    0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(),
    0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
    0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
    0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt()
)

private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

private fun sha256(message: ByteArray): ByteArray {
    val bitLen = message.size.toLong() * 8
    // Padding: append 1-bit, then zeros, then 64-bit big-endian length
    val padded = run {
        val extra = (56 - (message.size + 1) % 64 + 64) % 64
        val buf = ByteArray(message.size + 1 + extra + 8)
        message.copyInto(buf)
        buf[message.size] = 0x80.toByte()
        for (i in 0..7) buf[buf.size - 1 - i] = (bitLen ushr (i * 8)).toByte()
        buf
    }

    var h0 = 0x6a09e667u.toInt()
    var h1 = 0xbb67ae85u.toInt()
    var h2 = 0x3c6ef372u.toInt()
    var h3 = 0xa54ff53au.toInt()
    var h4 = 0x510e527fu.toInt()
    var h5 = 0x9b05688cu.toInt()
    var h6 = 0x1f83d9abu.toInt()
    var h7 = 0x5be0cd19u.toInt()

    val w = IntArray(64)
    for (chunk in 0 until padded.size / 64) {
        val off = chunk * 64
        for (i in 0..15) {
            w[i] = ((padded[off + i * 4].toInt() and 0xFF) shl 24) or
                    ((padded[off + i * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((padded[off + i * 4 + 2].toInt() and 0xFF) shl 8) or
                    (padded[off + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16..63) {
            val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
            val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0; var b = h1; var c = h2; var d = h3
        var e = h4; var f = h5; var g = h6; var h = h7

        for (i in 0..63) {
            val S1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + S1 + ch + K[i] + w[i]
            val S0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = S0 + maj
            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h0 += a; h1 += b; h2 += c; h3 += d
        h4 += e; h5 += f; h6 += g; h7 += h
    }

    val result = ByteArray(32)
    for ((idx, v) in intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).withIndex()) {
        result[idx * 4] = (v ushr 24).toByte()
        result[idx * 4 + 1] = (v ushr 16).toByte()
        result[idx * 4 + 2] = (v ushr 8).toByte()
        result[idx * 4 + 3] = v.toByte()
    }
    return result
}
