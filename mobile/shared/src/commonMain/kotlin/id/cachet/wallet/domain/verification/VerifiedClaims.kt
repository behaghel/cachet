package id.cachet.wallet.domain.verification

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Result of cryptographically verifying an SD-JWT presentation.
 * Contains only claims whose disclosure hashes and issuer signatures have been checked.
 */
data class VerifiedClaims(
    val issuer: String,
    val claims: Map<String, JsonElement>,
    val issuedAt: Long? = null,       // epoch seconds
    val expiration: Long? = null,     // epoch seconds
    val status: JsonObject? = null,   // credentialStatus for revocation checking
    val holderBound: Boolean = false,
    val kbJwtNonce: String? = null,
    val kbJwtAud: String? = null
)
