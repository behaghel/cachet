package id.cachet.wallet.domain.sync

/**
 * Queue for consent receipts that need transparency log anchoring.
 * Receipts are enqueued when anchoring fails (e.g., offline) and
 * drained by SyncManager when connectivity is restored.
 */
interface AnchoringQueue {
    suspend fun enqueue(receiptId: String)
}
