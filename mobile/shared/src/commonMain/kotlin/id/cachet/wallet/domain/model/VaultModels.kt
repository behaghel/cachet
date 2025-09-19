package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VaultArtifact(
    val id: String,
    val type: String,
    val source: String,
    val payload: JsonElement,
    val createdAt: Instant
)

@Serializable
data class VaultPredicate(
    val id: String,
    val key: String,
    val value: String,
    val proofType: String?,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    val artifact: VaultArtifact?
)

data class VaultSnapshot(
    val predicates: List<VaultPredicate>
)
