package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.IssuanceQueue
import id.cachet.wallet.domain.sync.PendingIssuanceState

class FakeIssuanceQueue : IssuanceQueue {
    val enqueuedStates = mutableListOf<PendingIssuanceState>()

    override suspend fun enqueue(state: PendingIssuanceState) {
        enqueuedStates.add(state)
    }
}
