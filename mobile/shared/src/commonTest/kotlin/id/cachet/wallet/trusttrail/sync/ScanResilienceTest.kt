package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.model.EmailHeader
import id.cachet.wallet.trusttrail.provider.EmailProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanResilienceTest {

    private val oldDate = Instant.parse("2026-03-01T10:00:00Z")
    private val newDate = Instant.parse("2026-04-25T10:00:00Z")

    @Test
    fun `scan resumes from cursor - no re-fetch of old messages`() = runTest {
        val allHeaders = listOf(
            EmailHeader("vinted.es", "Old sale", oldDate, "msg-old"),
            EmailHeader("vinted.es", "New sale", newDate, "msg-new"),
        )

        val store = InMemoryScanStateStore()
        store.saveLastScanTimestamp("gmail", oldDate)

        val provider = CursorAwareFakeProvider(allHeaders)
        val scanner = ResumableScanner(provider, store, providerId = "gmail")

        val headers = scanner.fetchHeadersFromCursor()

        assertEquals(1, headers.size)
        assertEquals("msg-new", headers[0].messageId)
    }

    @Test
    fun `scan without cursor fetches all`() = runTest {
        val allHeaders = listOf(
            EmailHeader("vinted.es", "Old sale", oldDate, "msg-old"),
            EmailHeader("vinted.es", "New sale", newDate, "msg-new"),
        )

        val store = InMemoryScanStateStore()
        val provider = CursorAwareFakeProvider(allHeaders)
        val scanner = ResumableScanner(provider, store, providerId = "gmail")

        val headers = scanner.fetchHeadersFromCursor()

        assertEquals(2, headers.size)
    }

    @Test
    fun `cursor updated after commit`() = runTest {
        val headers = listOf(
            EmailHeader("vinted.es", "Sale 1", oldDate, "msg-1"),
            EmailHeader("vinted.es", "Sale 2", newDate, "msg-2"),
        )

        val store = InMemoryScanStateStore()
        val provider = CursorAwareFakeProvider(headers)
        val scanner = ResumableScanner(provider, store, providerId = "gmail")

        scanner.fetchHeadersFromCursor()
        scanner.commitCursor()

        assertEquals(newDate, store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `cursor not committed until explicit call`() = runTest {
        val headers = listOf(
            EmailHeader("vinted.es", "Sale", newDate, "msg-1"),
        )

        val store = InMemoryScanStateStore()
        val provider = CursorAwareFakeProvider(headers)
        val scanner = ResumableScanner(provider, store, providerId = "gmail")

        scanner.fetchHeadersFromCursor()
        // Don't call commitCursor()

        // Cursor should still be null — scan not committed
        assertEquals(null, store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `quota exception caught and reported as paused`() = runTest {
        val store = InMemoryScanStateStore()

        val provider = object : EmailProvider {
            override suspend fun fetchHeaders(scanDepthMonths: Int): List<EmailHeader> {
                throw QuotaExhaustedException("429 Too Many Requests")
            }
            override suspend fun fetchFullContent(messageId: String) = null
            override fun isConnected() = true
        }

        val scanner = ResumableScanner(provider, store, providerId = "gmail")

        val result = scanner.fetchHeadersSafe()

        assertTrue(result.quotaExhausted)
        assertTrue(result.headers.isEmpty())
        // Cursor should not advance
        assertEquals(null, store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `disconnect clears cursor and state`() {
        val store = InMemoryScanStateStore()
        store.saveLastScanTimestamp("gmail", newDate)

        store.clear("gmail")

        assertEquals(null, store.getLastScanTimestamp("gmail"))
    }
}

/**
 * Fake provider that returns all headers — cursor filtering is done by ResumableScanner.
 */
class CursorAwareFakeProvider(
    private val allHeaders: List<EmailHeader>,
) : EmailProvider {
    override suspend fun fetchHeaders(scanDepthMonths: Int) = allHeaders
    override suspend fun fetchFullContent(messageId: String): EmailProvider.RawEmail? = null
    override fun isConnected() = true
}
