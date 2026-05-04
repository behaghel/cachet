package id.cachet.wallet.trusttrail.provider

/**
 * Gmail API configuration constants.
 * Scopes and API parameters — no secrets, no tokens.
 */
object GmailConfig {

    /** OAuth scope: read-only access to Gmail messages. */
    const val OAUTH_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    /** All scopes requested during OAuth consent. */
    val allScopes: List<String> = listOf(OAUTH_SCOPE)

    /** Default scan depth for cold start discovery. */
    const val DEFAULT_SCAN_DEPTH_MONTHS = 6

    /** Gmail API format parameter for headers-only fetch. */
    const val HEADERS_ONLY_FORMAT = "METADATA"

    /** Headers to request in METADATA format. */
    val METADATA_HEADERS: List<String> = listOf("From", "Subject", "Date")

    /** Gmail API base URL. */
    const val API_BASE_URL = "https://gmail.googleapis.com/gmail/v1"
}
