package id.cachet.wallet.domain.sync

import id.cachet.wallet.db.WalletDatabase

class SqlDelightSyncQueueRepository(
    private val database: WalletDatabase
) : SyncQueueRepository {

    override suspend fun getPendingAnchorings(): List<SyncQueueRepository.PendingAnchoringItem> {
        return database.walletDatabaseQueries.getPendingAnchorings().executeAsList().map {
            SyncQueueRepository.PendingAnchoringItem(
                receiptId = it.receipt_id,
                retryCount = it.retry_count,
                status = it.status
            )
        }
    }

    override suspend fun deletePendingAnchoring(receiptId: String) {
        database.walletDatabaseQueries.deletePendingAnchoring(receiptId)
    }

    override suspend fun updatePendingAnchoringStatus(
        receiptId: String, status: String, retryCount: Long, lastAttemptAt: Long
    ) {
        database.walletDatabaseQueries.updatePendingAnchoringStatus(
            status = status,
            retry_count = retryCount,
            last_attempt_at = lastAttemptAt,
            receipt_id = receiptId
        )
    }

    override suspend fun deleteExpiredIssuances(nowMillis: Long) {
        database.walletDatabaseQueries.deleteExpiredIssuances(nowMillis)
    }

    override suspend fun getPendingIssuances(): List<SyncQueueRepository.PendingIssuanceItem> {
        return database.walletDatabaseQueries.getPendingIssuances().executeAsList().map {
            SyncQueueRepository.PendingIssuanceItem(
                id = it.id,
                clientId = it.client_id,
                credentialTypesJson = it.credential_types_json,
                format = it.format,
                sessionId = it.session_id,
                accessToken = it.access_token,
                tokenExpiresAt = it.token_expires_at,
                keyAlias = it.key_alias,
                holderJwk = it.holder_jwk,
                retryCount = it.retry_count,
                status = it.status
            )
        }
    }

    override suspend fun deletePendingIssuance(id: String) {
        database.walletDatabaseQueries.deletePendingIssuance(id)
    }

    override suspend fun updatePendingIssuanceStatus(
        id: String, status: String, retryCount: Long, lastAttemptAt: Long
    ) {
        database.walletDatabaseQueries.updatePendingIssuanceStatus(
            status = status,
            retry_count = retryCount,
            last_attempt_at = lastAttemptAt,
            id = id
        )
    }

    override suspend fun getPendingAnchoringCount(): Int {
        return database.walletDatabaseQueries.getPendingAnchorings().executeAsList().size
    }

    override suspend fun getPendingIssuanceCount(): Int {
        return database.walletDatabaseQueries.getPendingIssuances().executeAsList().size
    }
}
