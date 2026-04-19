package id.cachet.wallet.domain.verification

import id.cachet.wallet.domain.crypto.Base64Url
import id.cachet.wallet.domain.crypto.JWSVerifier
import kotlin.time.Clock
import kotlinx.serialization.json.*

/**
 * Verifies Key Binding JWTs (KB-JWT) for holder binding.
 *
 * Checks: signature against cnf.jwk, typ == "kb+jwt", sd_hash integrity, iat freshness (5 min).
 *
 * Port of Go `services/verifier/internal/eval/kbjwt.go`.
 */
class KBJWTVerifier(
    private val jwsVerifier: JWSVerifier = JWSVerifier(),
    private val clock: Clock = Clock.System
) {
    companion object {
        private const val MAX_AGE_SECONDS = 5 * 60L // 5 minutes
    }

    data class KBJWTResult(
        val nonce: String,
        val aud: String,
        val sdHash: String,
        val issuedAt: Long
    )

    /**
     * Verify a KB-JWT against the holder's cnf claim and expected sd_hash.
     *
     * @param kbJwtString The KB-JWT compact serialization
     * @param cnf The cnf claim from the issuer JWT (contains holder's public key JWK)
     * @param expectedSDHash The expected sd_hash (base64url(sha256(issuerJWT~disc1~...~)))
     * @return Verified KB-JWT claims
     * @throws IllegalStateException on verification failure
     */
    fun verify(kbJwtString: String, cnf: JsonObject, expectedSDHash: String): KBJWTResult {
        require(kbJwtString.isNotEmpty()) { "KB-JWT is empty" }

        // Extract holder's public key from cnf.jwk
        val holderKeyJWK = extractCNFKeyJWK(cnf)

        // Verify signature using JWSVerifier
        val payloadJson = jwsVerifier.verifyJWS(kbJwtString, holderKeyJWK, "kb+jwt")
        val claims = Json.parseToJsonElement(payloadJson).jsonObject

        // Verify sd_hash matches
        val sdHash = claims["sd_hash"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing sd_hash in KB-JWT")
        if (sdHash != expectedSDHash) {
            throw IllegalStateException("sd_hash mismatch: KB-JWT binds to different disclosure set")
        }

        // Check iat freshness (max 5 minutes old)
        val iat = claims["iat"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalStateException("Missing or invalid iat in KB-JWT")
        val now = clock.now().epochSeconds
        if ((now - iat) > MAX_AGE_SECONDS) {
            throw IllegalStateException("KB-JWT expired: issued ${now - iat}s ago (max ${MAX_AGE_SECONDS}s)")
        }

        return KBJWTResult(
            nonce = claims["nonce"]?.jsonPrimitive?.content ?: "",
            aud = claims["aud"]?.jsonPrimitive?.content ?: "",
            sdHash = sdHash,
            issuedAt = iat
        )
    }

    /**
     * Extract the holder's public key JWK JSON string from the cnf claim.
     * Expected: cnf: { jwk: { kty: "EC", crv: "P-256", x: "...", y: "..." } }
     */
    private fun extractCNFKeyJWK(cnf: JsonObject): String {
        val jwk = cnf["jwk"]?.jsonObject
            ?: throw IllegalStateException("cnf.jwk not found")

        val kty = jwk["kty"]?.jsonPrimitive?.content
        if (kty != "EC") throw IllegalStateException("Unsupported key type: $kty (expected EC)")

        val crv = jwk["crv"]?.jsonPrimitive?.content
        if (crv != "P-256") throw IllegalStateException("Unsupported curve: $crv (expected P-256)")

        return jwk.toString()
    }
}
