package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Typed credential subject matching the OpenAPI spec.
 * Replaces Map<String, JsonElement> for type safety.
 */
@Serializable
data class CredentialSubject(
    val id: String,
    val personalData: PersonalData? = null,
    val verificationLevel: String? = null,
    val verified: Boolean? = null,
    val verificationMethod: String? = null,
    val verificationMetrics: VerificationMetrics? = null,
    val evidence: List<VerificationEvidence>? = null
)

@Serializable
data class PersonalData(
    val age: Int? = null,
    val nationality: String? = null,
    val documentType: String? = null
)

@Serializable
data class VerificationMetrics(
    val overallConfidence: Double? = null,
    val livenessScore: Double? = null,
    val documentAuthenticity: Double? = null,
    val riskScore: Double? = null,
    val sessionTimestamp: String? = null
)

@Serializable
data class VerificationEvidence(
    val type: String? = null,
    val sessionId: String? = null,
    val verifier: String? = null,
    val status: String? = null
)
