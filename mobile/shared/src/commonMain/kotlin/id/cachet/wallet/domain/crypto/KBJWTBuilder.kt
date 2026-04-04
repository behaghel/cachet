package id.cachet.wallet.domain.crypto

import id.cachet.wallet.domain.model.sha256Hash
import kotlinx.datetime.Clock

/**
 * Builds Key Binding JWTs (KB-JWT) for SD-JWT presentations.
 *
 * A KB-JWT proves the presenter holds the private key bound to the credential
 * via the cnf claim. It contains:
 * - nonce: verifier-supplied challenge (replay protection)
 * - aud: verifier's DID (audience binding)
 * - iat: current timestamp
 * - sd_hash: hash of the SD-JWT + disclosures being presented
 */
object KBJWTBuilder {

    /**
     * Build a KB-JWT for an SD-JWT presentation.
     *
     * @param nonce Verifier-supplied nonce for replay protection
     * @param audience Verifier's DID for audience binding
     * @param sdJwtWithDisclosures The SD-JWT content being presented (issuerJWT~disc1~disc2~...~)
     * @param keyManager Platform key manager for signing
     * @param keyAlias Alias of the holder's private key in the key store
     * @return The signed KB-JWT string (header.payload.signature)
     */
    fun build(
        nonce: String,
        audience: String,
        sdJwtWithDisclosures: String,
        keyManager: KeyManager,
        keyAlias: String
    ): String {
        val header = """{"alg":"ES256","typ":"kb+jwt"}"""
        val sdHash = computeSDHash(sdJwtWithDisclosures)
        val iat = Clock.System.now().epochSeconds
        val payload = """{"nonce":"$nonce","aud":"$audience","iat":$iat,"sd_hash":"$sdHash"}"""

        val headerEncoded = base64UrlEncode(header.encodeToByteArray())
        val payloadEncoded = base64UrlEncode(payload.encodeToByteArray())
        val signingInput = "$headerEncoded.$payloadEncoded"

        val signatureBytes = keyManager.sign(keyAlias, signingInput.encodeToByteArray())
        val signatureEncoded = base64UrlEncode(signatureBytes)

        return "$signingInput.$signatureEncoded"
    }

    /**
     * Compute the sd_hash: base64url(sha256(sdJwtWithDisclosures))
     */
    fun computeSDHash(sdJwtContent: String): String {
        // sha256Hash returns hex string, we need the raw bytes for base64url
        val hexHash = sha256Hash(sdJwtContent)
        val hashBytes = hexHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return base64UrlEncode(hashBytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        val base64 = bytes.toBase64()
        return base64
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }
}

// Multiplatform base64 encoding (simple implementation)
internal fun ByteArray.toBase64(): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
        val remaining = size - i

        sb.append(table[b0 shr 2])
        sb.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
        if (remaining > 1) sb.append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)]) else sb.append('=')
        if (remaining > 2) sb.append(table[b2 and 0x3F]) else sb.append('=')
        i += 3
    }
    return sb.toString()
}
