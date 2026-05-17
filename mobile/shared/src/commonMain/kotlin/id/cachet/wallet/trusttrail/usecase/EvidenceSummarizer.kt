package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.model.PlatformSummary

/**
 * Groups extracted evidence by platform for the review screen.
 */
object EvidenceSummarizer {

    /**
     * Group evidence by platform, excluding rejected emails.
     * Returns summaries sorted by total claim count descending.
     */
    fun groupByPlatform(evidenceList: List<EmailEvidence>): List<PlatformSummary> {
        return evidenceList
            .filter { !it.rejected }
            .groupBy { it.platform }
            .map { (platform, items) ->
                val allClaims = items.flatMap { it.claims }
                PlatformSummary(
                    platform = platform,
                    emailCount = items.size,
                    totalClaims = allClaims.size,
                    claims = allClaims,
                )
            }
            .sortedByDescending { it.totalClaims }
    }
}
