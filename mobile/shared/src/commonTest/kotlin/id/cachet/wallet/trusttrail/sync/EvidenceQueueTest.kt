package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.model.BundleClaim
import id.cachet.wallet.trusttrail.model.EvidenceBundle
import id.cachet.wallet.trusttrail.model.TrustLevel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvidenceQueueTest {

    @Test
    fun `failed submission queued for retry`() = runTest {
        val queue = InMemoryEvidenceQueue()
        val bundle = makeBundle()

        queue.enqueue(bundle)

        assertEquals(1, queue.pendingCount())
        assertEquals(bundle, queue.peek())
    }

    @Test
    fun `successful retry removes from queue`() = runTest {
        val queue = InMemoryEvidenceQueue()
        val bundle = makeBundle()

        queue.enqueue(bundle)
        queue.markCompleted()

        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun `multiple bundles queued in order`() = runTest {
        val queue = InMemoryEvidenceQueue()
        val bundle1 = makeBundle("vinted")
        val bundle2 = makeBundle("care.com")

        queue.enqueue(bundle1)
        queue.enqueue(bundle2)

        assertEquals(2, queue.pendingCount())
        assertEquals(bundle1, queue.peek())

        queue.markCompleted()
        assertEquals(bundle2, queue.peek())
    }

    @Test
    fun `empty queue returns null peek`() = runTest {
        val queue = InMemoryEvidenceQueue()

        assertEquals(null, queue.peek())
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun `drain processes all pending`() = runTest {
        val queue = InMemoryEvidenceQueue()
        queue.enqueue(makeBundle("vinted"))
        queue.enqueue(makeBundle("care.com"))

        val submitted = mutableListOf<EvidenceBundle>()
        queue.drainTo { bundle ->
            submitted.add(bundle)
            true // success
        }

        assertEquals(2, submitted.size)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun `drain stops on failure and keeps remaining`() = runTest {
        val queue = InMemoryEvidenceQueue()
        queue.enqueue(makeBundle("vinted"))
        queue.enqueue(makeBundle("care.com"))

        var callCount = 0
        queue.drainTo { _ ->
            callCount++
            callCount <= 1 // fail on second
        }

        assertEquals(2, callCount)
        assertEquals(1, queue.pendingCount()) // second still queued
    }

    private fun makeBundle(platform: String = "vinted") = EvidenceBundle(
        claims = listOf(
            BundleClaim(
                type = "sale_notification",
                fields = emptyMap(),
                confidence = 0.95,
                trustLevel = TrustLevel.CRYPTOGRAPHIC,
                platform = platform,
                date = Instant.parse("2026-04-25T10:00:00Z"),
                dkimDomain = "$platform.test",
            )
        )
    )
}
