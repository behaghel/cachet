package id.cachet.wallet.trusttrail.strength

/**
 * Maps strength scores to metallic tiers and detects tier transitions.
 */
object TierResolver {

    /**
     * Resolve the tier for a given strength score.
     * Returns null if strength is below bronze threshold.
     */
    fun resolve(strength: Double, thresholds: TierThresholds): Tier? {
        return when {
            strength >= thresholds.gold -> Tier.GOLD
            strength >= thresholds.silver -> Tier.SILVER
            strength >= thresholds.bronze -> Tier.BRONZE
            else -> null
        }
    }

    /**
     * Check if the current strength has degraded below the credential's issued tier.
     * Used by the wallet to alert the holder.
     */
    fun isDegraded(
        currentStrength: Double,
        credentialTier: Tier,
        thresholds: TierThresholds,
    ): Boolean {
        val currentTier = resolve(currentStrength, thresholds)
        return currentTier == null || currentTier < credentialTier
    }

    /**
     * Check if the current strength has upgraded above the credential's issued tier.
     * Used to trigger auto-reissuance for a fresher/stronger credential.
     */
    fun isUpgraded(
        currentStrength: Double,
        credentialTier: Tier,
        thresholds: TierThresholds,
    ): Boolean {
        val currentTier = resolve(currentStrength, thresholds) ?: return false
        return currentTier > credentialTier
    }
}
