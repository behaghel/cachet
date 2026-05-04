package id.cachet.wallet.trusttrail.sync

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanCursorTest {

    @Test
    fun `new cursor starts at null`() {
        val store = InMemoryScanStateStore()
        assertNull(store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `cursor persists after update`() {
        val store = InMemoryScanStateStore()
        val timestamp = Instant.parse("2026-04-25T10:00:00Z")

        store.saveLastScanTimestamp("gmail", timestamp)

        assertEquals(timestamp, store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `cursor updated to latest timestamp`() {
        val store = InMemoryScanStateStore()
        val first = Instant.parse("2026-04-20T10:00:00Z")
        val second = Instant.parse("2026-04-25T10:00:00Z")

        store.saveLastScanTimestamp("gmail", first)
        store.saveLastScanTimestamp("gmail", second)

        assertEquals(second, store.getLastScanTimestamp("gmail"))
    }

    @Test
    fun `different providers have independent cursors`() {
        val store = InMemoryScanStateStore()
        val gmailTs = Instant.parse("2026-04-25T10:00:00Z")
        val outlookTs = Instant.parse("2026-04-20T10:00:00Z")

        store.saveLastScanTimestamp("gmail", gmailTs)
        store.saveLastScanTimestamp("outlook", outlookTs)

        assertEquals(gmailTs, store.getLastScanTimestamp("gmail"))
        assertEquals(outlookTs, store.getLastScanTimestamp("outlook"))
    }

    @Test
    fun `clear removes cursor`() {
        val store = InMemoryScanStateStore()
        store.saveLastScanTimestamp("gmail", Instant.parse("2026-04-25T10:00:00Z"))

        store.clear("gmail")

        assertNull(store.getLastScanTimestamp("gmail"))
    }
}
