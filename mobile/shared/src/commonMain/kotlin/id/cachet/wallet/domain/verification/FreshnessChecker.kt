package id.cachet.wallet.domain.verification

import kotlin.time.Clock

/**
 * Checks credential freshness based on expiration and staleness thresholds.
 *
 * Port of Go `services/verifier/internal/eval/freshness.go`.
 */
object FreshnessChecker {

    private const val STALE_THRESHOLD_SECONDS = 90L * 24 * 60 * 60 // 90 days

    /**
     * Check freshness of a credential given its iat and exp (epoch seconds).
     * Returns "ok", "stale", or "expired".
     */
    fun check(issuedAtSeconds: Long?, expirationSeconds: Long?): String {
        val nowSeconds = Clock.System.now().epochSeconds

        // Check expiration
        if (expirationSeconds != null && nowSeconds > expirationSeconds) {
            return "expired"
        }

        // Check staleness (>90 days since issuance)
        if (issuedAtSeconds != null && (nowSeconds - issuedAtSeconds) > STALE_THRESHOLD_SECONDS) {
            return "stale"
        }

        return "ok"
    }
}
