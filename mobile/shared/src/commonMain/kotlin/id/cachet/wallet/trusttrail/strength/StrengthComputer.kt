package id.cachet.wallet.trusttrail.strength

import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Computes cachet strength from evidence items with temporal decay.
 *
 * Linear decay: score × max(0, 1 - age_months / window_months)
 * Normalization: capped at 1.0 — more evidence beyond the normalization
 * factor doesn't increase strength further.
 */
object StrengthComputer {

    /**
     * Compute the decayed score of a single evidence item.
     *
     * @param item The evidence item
     * @param now Current time
     * @param config Decay configuration
     * @return Decayed score in [0.0, item.score]
     */
    fun decayedScore(item: EvidenceItem, now: Instant, config: DecayConfig): Double {
        val ageMonths = ageInMonths(item.date, now)
        if (ageMonths <= 0.0) return item.score // future or same-day evidence
        val decayFactor = max(0.0, 1.0 - ageMonths / config.windowMonths)
        return item.score * decayFactor
    }

    /**
     * Compute composite strength from all evidence items.
     *
     * @param items All evidence items in the credential
     * @param now Current time
     * @param config Decay configuration
     * @param normalizationFactor Number of perfect-score items that equals 1.0
     *   (e.g., 10.0 for Trusted Host = 10 confirmed exchanges at score 1.0)
     * @return Strength in [0.0, 1.0]
     */
    fun computeStrength(
        items: List<EvidenceItem>,
        now: Instant,
        config: DecayConfig,
        normalizationFactor: Double,
    ): Double {
        if (items.isEmpty()) return 0.0
        val totalDecayed = items.sumOf { decayedScore(it, now, config) }
        return min(1.0, totalDecayed / normalizationFactor)
    }

    /**
     * Approximate age in months between two instants.
     * Uses 30.44 days per month (average).
     */
    private fun ageInMonths(date: Instant, now: Instant): Double {
        val diffSeconds = (now - date).inWholeSeconds
        val diffDays = diffSeconds / 86400.0
        return diffDays / 30.44
    }
}
