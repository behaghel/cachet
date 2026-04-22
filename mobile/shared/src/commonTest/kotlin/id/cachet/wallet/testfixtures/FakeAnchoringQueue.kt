package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.AnchoringQueue

class FakeAnchoringQueue : AnchoringQueue {
    val enqueuedReceiptIds = mutableListOf<String>()

    override suspend fun enqueue(receiptId: String) {
        enqueuedReceiptIds.add(receiptId)
    }
}
