package id.cachet.wallet.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Full pack definition matching the registry JSON schema.
 * Cached locally for offline verification.
 */
@Serializable
data class PackDefinition(
    val id: String,
    val version: String,
    val name: String,
    val purpose: String,
    val jurisdictions: List<String>,
    val badge: PackBadge,
    val predicates: List<PackPredicate>
)

@Serializable
data class PackBadge(
    val label: String,
    val ttl: String,
    val jurisdiction: String
)

@Serializable
data class PackPredicate(
    val id: String,
    val claim: String,
    val operator: String,
    val value: JsonPrimitive,
    val issuersAccepted: List<String>,
    val proofType: String,
    val required: Boolean = true
)
