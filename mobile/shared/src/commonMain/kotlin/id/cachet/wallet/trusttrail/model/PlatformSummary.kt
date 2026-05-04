package id.cachet.wallet.trusttrail.model

/**
 * Summary of extracted evidence for one platform.
 * Used to present the evidence review screen.
 */
data class PlatformSummary(
    val platform: String,
    val emailCount: Int,
    val totalClaims: Int,
    val claims: List<Claim>,
)
