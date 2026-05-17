package id.cachet.wallet.trusttrail.strength

import id.cachet.wallet.trusttrail.model.TrustLevel
import kotlinx.datetime.Instant

/**
 * A single piece of evidence stored inside the credential.
 * Used for strength computation with temporal decay.
 */
data class EvidenceItem(
    val type: String,
    val platform: String,
    val date: Instant,
    val trustLevel: TrustLevel,
    val score: Double,
    val dkimDomain: String,
)
