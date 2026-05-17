package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.extraction.ClaimExtractor
import id.cachet.wallet.trusttrail.model.DiscoveredPlatform
import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider

/**
 * Orchestrates a single polling cycle:
 * 1. Fetch new headers since last cursor
 * 2. Separate into consented platform emails (new headers) and
 *    unconsented known platforms (suggestions)
 * 3. Advance cursor on success
 */
class PollingOrchestrator(
    private val provider: EmailProvider,
    private val stateStore: ScanStateStore,
    private val providerId: String,
    private val consentedPlatforms: Set<String>,
) {

    /**
     * Execute one polling cycle.
     * Returns new headers for consented platforms and suggestions for new platforms.
     */
    suspend fun poll(): PollResult {
        val scanner = ResumableScanner(provider, stateStore, providerId)
        val headers = scanner.fetchHeadersFromCursor()

        val newHeaders = mutableListOf<EmailHeader>()
        val suggestionGroups = mutableMapOf<String, MutableList<String>>()

        for (header in headers) {
            val platform = ClaimExtractor.detectPlatform(header.fromDomain) ?: continue

            if (platform in consentedPlatforms) {
                newHeaders.add(header)
            } else {
                suggestionGroups.getOrPut(platform) { mutableListOf() }.add(header.messageId)
            }
        }

        val suggestions = suggestionGroups.map { (platform, ids) ->
            DiscoveredPlatform(
                platform = platform,
                emailCount = ids.size,
                messageIds = ids,
            )
        }

        // Advance cursor after successful poll
        scanner.commitCursor()

        return PollResult(
            newHeaders = newHeaders,
            newPlatformSuggestions = suggestions,
        )
    }

    data class PollResult(
        val newHeaders: List<EmailHeader>,
        val newPlatformSuggestions: List<DiscoveredPlatform>,
    )
}
