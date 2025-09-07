package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a consent receipt that proves user gave informed consent
 * for their credential data to be processed in a specific way
 */
@Serializable
data class ConsentReceipt(
    val id: String,
    val timestamp: Instant,
    val purpose: String,
    val predicatesProven: List<String>,
    val rpIdentifier: String,
    val rpDisplayName: String,
    val userConsent: ConsentDetails,
    val credentialId: String,
    val receiptHash: String? = null,
    val signature: String? = null,
    val salt: String? = null
) {
    /**
     * Generate a cryptographically secure hash of this consent receipt for transparency logging
     */
    fun generateHash(salt: String = generateSalt()): String {
        val canonicalContent = buildCanonicalRepresentation()
        val saltedContent = "$canonicalContent|salt:$salt"
        return "sha256:" + sha256Hash(saltedContent)
    }

    /**
     * Build canonical string representation for consistent hashing
     */
    fun buildCanonicalRepresentation(): String {
        return listOf(
            "id:$id",
            "timestamp:$timestamp",
            "purpose:$purpose", 
            "predicates:${predicatesProven.sorted().joinToString(",")}",
            "rp:$rpIdentifier",
            "credential:$credentialId",
            "consent:${userConsent.toCanonicalString()}"
        ).joinToString("|")
    }
}

/**
 * Details about the user's consent acknowledgment
 */
@Serializable
data class ConsentDetails(
    val explicitConsent: Boolean,
    val dataMinimizationAcknowledged: Boolean,
    val retentionPeriodUnderstood: Boolean,
    val retentionPeriodDays: Int = 90,
    val revocationRightsUnderstood: Boolean = true
) {
    /**
     * Build canonical string representation for consistent hashing
     */
    fun toCanonicalString(): String {
        return listOf(
            "explicit:$explicitConsent",
            "minimization:$dataMinimizationAcknowledged", 
            "retention:$retentionPeriodUnderstood:$retentionPeriodDays",
            "revocation:$revocationRightsUnderstood"
        ).joinToString(",")
    }
}

/**
 * Request to present a credential with specific predicates
 */
@Serializable
data class PresentationRequest(
    val rpIdentifier: String,
    val rpDisplayName: String,
    val purpose: String,
    val requestedPredicates: List<String>,
    val retentionPeriod: String = "P90D" // ISO 8601 duration
)

/**
 * Result of a credential presentation
 */
@Serializable
data class PresentationResult(
    val success: Boolean,
    val predicatesProven: List<String>,
    val consentReceipt: ConsentReceipt?,
    val errorMessage: String? = null
)

/**
 * Represents what predicates can be proven from a credential
 */
@Serializable
data class AvailablePredicate(
    val id: String,
    val description: String,
    val canProve: Boolean,
    val requiresConsent: Boolean = true
)

/**
 * Extension functions for working with consent receipts
 */

/**
 * Extract available predicates from a verifiable credential
 */
fun VerifiableCredential.getAvailablePredicates(): List<AvailablePredicate> {
    val predicates = mutableListOf<AvailablePredicate>()
    
    // Age predicates
    val personalData = credentialSubject["personalData"]
    if (personalData != null) {
        predicates.add(
            AvailablePredicate(
                id = "age_gte_18",
                description = "Age is 18 or older",
                canProve = true
            )
        )
        predicates.add(
            AvailablePredicate(
                id = "age_gte_21", 
                description = "Age is 21 or older",
                canProve = true
            )
        )
    }
    
    // Identity verification predicates
    val verified = credentialSubject["verified"]
    if (verified != null) {
        predicates.add(
            AvailablePredicate(
                id = "identity_verified",
                description = "Identity has been verified with government ID",
                canProve = true
            )
        )
        predicates.add(
            AvailablePredicate(
                id = "liveness_verified", 
                description = "Liveness check passed (not a photo)",
                canProve = true
            )
        )
    }
    
    return predicates
}

/**
 * Check if this credential can satisfy a presentation request
 */
fun VerifiableCredential.canSatisfyRequest(request: PresentationRequest): Boolean {
    val available = getAvailablePredicates().map { it.id }
    return request.requestedPredicates.all { it in available }
}

/**
 * Cryptographic helper functions for consent receipt tamper-proofing
 */

/**
 * Generate a cryptographically secure random salt
 */
fun generateSalt(length: Int = 32): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
        .map { chars.random() }
        .joinToString("")
}

/**
 * Compute SHA-256 hash of input string
 * In production, this would use a proper cryptographic library
 */
expect fun sha256Hash(input: String): String

/**
 * Generate EdDSA signature for consent receipt
 * In production, this would use proper EdDSA signing with private key
 */
expect fun signConsentReceipt(canonicalContent: String, privateKey: String): String

/**
 * Verify EdDSA signature for consent receipt  
 * In production, this would use proper EdDSA verification with public key
 */
expect fun verifyConsentReceiptSignature(
    canonicalContent: String, 
    signature: String, 
    publicKey: String
): Boolean