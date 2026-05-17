package id.cachet.wallet.trusttrail.strength

/**
 * Metallic tier — human-readable abstraction of the strength score.
 */
enum class Tier {
    BRONZE,
    SILVER,
    GOLD,
}

/**
 * Per-cachet-type thresholds for tier assignment.
 */
data class TierThresholds(
    val bronze: Double = 0.3,
    val silver: Double = 0.6,
    val gold: Double = 0.85,
)
