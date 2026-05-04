package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider

/**
 * Exception thrown when the email provider's API quota is exhausted (HTTP 429).
 */
class QuotaExhaustedException(message: String) : Exception(message)

/**
 * Wraps an EmailProvider with cursor-based resumption and quota handling.
 *
 * - Filters headers older than the saved cursor
 * - Tracks the latest timestamp for cursor advancement
 * - Catches QuotaExhaustedException without losing progress
 */
class ResumableScanner(
    private val provider: EmailProvider,
    private val stateStore: ScanStateStore,
    private val providerId: String,
) {

    private var latestTimestamp: kotlinx.datetime.Instant? = null

    /**
     * Fetch headers, filtering out messages already scanned (older than cursor).
     */
    suspend fun fetchHeadersFromCursor(): List<EmailHeader> {
        val cursor = stateStore.getLastScanTimestamp(providerId)
        val allHeaders = provider.fetchHeaders()

        val filtered = if (cursor != null) {
            allHeaders.filter { it.date > cursor }
        } else {
            allHeaders
        }

        latestTimestamp = filtered.maxByOrNull { it.date }?.date
        return filtered
    }

    /**
     * Fetch headers with quota error handling.
     * Returns empty list with quotaExhausted=true if API quota is hit.
     * Cursor is NOT advanced on quota errors.
     */
    suspend fun fetchHeadersSafe(): ScanResult {
        return try {
            val headers = fetchHeadersFromCursor()
            ScanResult(headers = headers, quotaExhausted = false)
        } catch (_: QuotaExhaustedException) {
            ScanResult(headers = emptyList(), quotaExhausted = true)
        }
    }

    /**
     * Advance the cursor to the latest scanned timestamp.
     * Call only after a successful, complete scan.
     */
    fun commitCursor() {
        val ts = latestTimestamp ?: return
        stateStore.saveLastScanTimestamp(providerId, ts)
    }

    data class ScanResult(
        val headers: List<EmailHeader>,
        val quotaExhausted: Boolean,
    )
}
