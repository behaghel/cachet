package id.cachet.wallet.trusttrail.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanProgressTest {

    @Test
    fun `initial progress is zero`() {
        val progress = ScanProgress(scanned = 0, total = 100)
        assertEquals(0, progress.scanned)
        assertEquals(100, progress.total)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `progress tracks scanned count`() {
        val progress = ScanProgress(scanned = 42, total = 100)
        assertEquals(42, progress.scanned)
        assertEquals(0.42f, progress.fraction, 0.01f)
    }

    @Test
    fun `complete when scanned equals total`() {
        val progress = ScanProgress(scanned = 100, total = 100)
        assertTrue(progress.isComplete)
        assertEquals(1.0f, progress.fraction, 0.01f)
    }

    @Test
    fun `zero total yields complete`() {
        val progress = ScanProgress(scanned = 0, total = 0)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `paused state reported`() {
        val progress = ScanProgress(scanned = 50, total = 100, paused = true, pauseReason = "quota_exhausted")
        assertTrue(progress.paused)
        assertEquals("quota_exhausted", progress.pauseReason)
    }
}
