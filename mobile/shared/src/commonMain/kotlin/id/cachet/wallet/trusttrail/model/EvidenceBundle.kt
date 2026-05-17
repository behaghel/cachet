package id.cachet.wallet.trusttrail.model

import kotlinx.datetime.Instant

/**
 * The evidence bundle submitted to the issuance gateway.
 * Contains only user-approved, structured claims — no raw email content.
 */
data class EvidenceBundle(
    val claims: List<BundleClaim>,
)

/**
 * A single claim in the evidence bundle, enriched with provenance metadata.
 */
data class BundleClaim(
    val type: String,
    val fields: Map<String, String>,
    val confidence: Double,
    val trustLevel: TrustLevel?,
    val platform: String,
    val date: Instant,
    val dkimDomain: String?,
)
