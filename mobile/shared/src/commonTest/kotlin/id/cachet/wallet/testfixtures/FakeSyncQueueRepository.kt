package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.SyncQueueRepository

class FakeSyncQueueRepository : SyncQueueRepository {

    val anchoringItems = mutableListOf<SyncQueueRepository.PendingAnchoringItem>()
    val issuanceItems = mutableListOf<SyncQueueRepository.PendingIssuanceItem>()

    // ── Anchoring ──

    override suspend fun getPendingAnchorings(): List<SyncQueueRepository.PendingAnchoringItem> {
        return anchoringItems.filter { it.status == "pending" }
    }

    override suspend fun deletePendingAnchoring(receiptId: String) {
        anchoringItems.removeAll { it.receiptId == receiptId }
    }

    override suspend fun updatePendingAnchoringStatus(
        receiptId: String, status: String, retryCount: Long, lastAttemptAt: Long
    ) {
        val index = anchoringItems.indexOfFirst { it.receiptId == receiptId }
        if (index >= 0) {
            anchoringItems[index] = anchoringItems[index].copy(
                status = status,
                retryCount = retryCount
            )
        }
    }

    // ── Issuance ──

    override suspend fun deleteExpiredIssuances(nowMillis: Long) {
        issuanceItems.removeAll { it.tokenExpiresAt < nowMillis }
    }

    override suspend fun getPendingIssuances(): List<SyncQueueRepository.PendingIssuanceItem> {
        return issuanceItems.filter { it.status == "pending" }
    }

    override suspend fun deletePendingIssuance(id: String) {
        issuanceItems.removeAll { it.id == id }
    }

    override suspend fun updatePendingIssuanceStatus(
        id: String, status: String, retryCount: Long, lastAttemptAt: Long
    ) {
        val index = issuanceItems.indexOfFirst { it.id == id }
        if (index >= 0) {
            issuanceItems[index] = issuanceItems[index].copy(
                status = status,
                retryCount = retryCount
            )
        }
    }

    // ── Counts ──

    override suspend fun getPendingAnchoringCount(): Int =
        anchoringItems.count { it.status == "pending" }

    override suspend fun getPendingIssuanceCount(): Int =
        issuanceItems.count { it.status == "pending" }
}
