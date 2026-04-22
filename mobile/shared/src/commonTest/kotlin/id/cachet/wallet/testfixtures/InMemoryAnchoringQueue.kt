package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.AnchoringQueue
import id.cachet.wallet.domain.sync.SyncQueueRepository
import kotlin.time.Clock

/**
 * AnchoringQueue backed by FakeSyncQueueRepository —
 * bridges ConsentUseCase enqueue calls to the same store SyncManager reads from.
 */
class InMemoryAnchoringQueue(
    private val queueRepository: FakeSyncQueueRepository,
    private val clock: Clock = Clock.System
) : AnchoringQueue {

    override suspend fun enqueue(receiptId: String) {
        queueRepository.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem(
                receiptId = receiptId,
                retryCount = 0,
                status = "pending"
            )
        )
    }
}
