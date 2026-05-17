package id.cachet.wallet.trusttrail.sync

import kotlinx.datetime.Instant

/**
 * Persists scan cursor state — the timestamp of the last successfully scanned message.
 * Enables scan resumption across app restarts.
 *
 * Implementations: InMemoryScanStateStore (tests), SQLDelight (production).
 */
interface ScanStateStore {
    fun getLastScanTimestamp(providerId: String): Instant?
    fun saveLastScanTimestamp(providerId: String, timestamp: Instant)
    fun clear(providerId: String)
}

/**
 * In-memory implementation for testing.
 */
class InMemoryScanStateStore : ScanStateStore {
    private val cursors = mutableMapOf<String, Instant>()

    override fun getLastScanTimestamp(providerId: String): Instant? = cursors[providerId]

    override fun saveLastScanTimestamp(providerId: String, timestamp: Instant) {
        cursors[providerId] = timestamp
    }

    override fun clear(providerId: String) {
        cursors.remove(providerId)
    }
}
