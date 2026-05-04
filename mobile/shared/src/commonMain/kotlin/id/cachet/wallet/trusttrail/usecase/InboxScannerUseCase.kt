package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.extraction.ClaimExtractor
import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailEvidence
import id.cachet.wallet.trusttrail.provider.EmailProvider
import kotlinx.datetime.Instant

/**
 * Orchestrates inbox scanning for behavioral evidence.
 *
 * Phase 1: platform discovery via headers-only scan.
 * Phase 2: full extraction for user-consented platforms only.
 */
class InboxScannerUseCase(
    private val provider: EmailProvider,
    private val confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD,
) {

    /**
     * Scan inbox headers and identify which known platforms have emails.
     * Only fetches headers (From, Subject, Date) — no body content.
     *
     * Returns discovered platforms sorted by email count (descending).
     */
    suspend fun discoverPlatforms(): List<DiscoveredPlatform> {
        val headers = provider.fetchHeaders()

        val platformGroups = headers
            .mapNotNull { header ->
                val platform = ClaimExtractor.detectPlatform(header.fromDomain)
                if (platform != null) platform to header.messageId else null
            }
            .groupBy({ it.first }, { it.second })

        return platformGroups
            .map { (platform, messageIds) ->
                DiscoveredPlatform(
                    platform = platform,
                    emailCount = messageIds.size,
                    messageIds = messageIds,
                )
            }
            .sortedByDescending { it.emailCount }
    }

    /**
     * Fetch full content and extract claims for consented platforms only.
     * Each message is fetched individually, extracted, then the raw content
     * is discarded — only structured claims survive.
     *
     * Claims below the confidence threshold are filtered out.
     */
    suspend fun extractClaims(
        consentedPlatforms: List<DiscoveredPlatform>,
    ): List<EmailEvidence> {
        val results = mutableListOf<EmailEvidence>()

        for (platform in consentedPlatforms) {
            for (messageId in platform.messageIds) {
                val raw = provider.fetchFullContent(messageId) ?: continue

                val evidence = ClaimExtractor.extract(
                    from = raw.from,
                    subject = raw.subject,
                    textBody = raw.textBody,
                    htmlBody = raw.htmlBody,
                    date = Instant.fromEpochMilliseconds(0), // TODO: parse from raw
                )

                // Filter claims below confidence threshold
                val filteredClaims = ClaimExtractor.filterByConfidence(
                    evidence.claims, confidenceThreshold,
                )

                results.add(evidence.copy(claims = filteredClaims))
            }
        }

        return results
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7
    }
}
