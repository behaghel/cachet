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
    val signature: String? = null
) {
    /**
     * Generate a hash of this consent receipt for transparency logging
     */
    fun generateHash(): String {
        // In production, this would use a proper cryptographic hash
        val content = "$id$timestamp$purpose${predicatesProven.joinToString()}$rpIdentifier$credentialId"
        return "sha256:" + content.hashCode().toString(16).padStart(8, '0')
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
)

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