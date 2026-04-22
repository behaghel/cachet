package id.cachet.wallet.domain.sync

import id.cachet.wallet.db.WalletDatabase
import kotlin.time.Clock

class SqlDelightAnchoringQueue(
    private val database: WalletDatabase,
    private val clock: Clock = Clock.System
) : AnchoringQueue {

    override suspend fun enqueue(receiptId: String) {
        database.walletDatabaseQueries.insertPendingAnchoring(
            receipt_id = receiptId,
            created_at = clock.now().toEpochMilliseconds(),
            retry_count = 0,
            last_attempt_at = null,
            status = "pending"
        )
    }
}
