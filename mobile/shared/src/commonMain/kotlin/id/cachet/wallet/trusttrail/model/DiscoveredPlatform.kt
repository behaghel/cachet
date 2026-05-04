package id.cachet.wallet.trusttrail.model

/**
 * A known platform discovered during the headers-only scan.
 * Presented to the user for consent before any full content is fetched.
 *
 * @property platform Canonical platform name (e.g., "vinted", "care.com")
 * @property emailCount Number of emails found from this platform
 * @property messageIds Provider-specific IDs of matching messages (for later full fetch)
 */
data class DiscoveredPlatform(
    val platform: String,
    val emailCount: Int,
    val messageIds: List<String>,
)
