package id.cachet.wallet.domain.sync

/**
 * Repository interface for sync queue persistence.
 * Abstracts SQLDelight queries for testability.
 */
interface SyncQueueRepository {

    // ── Anchoring queue ──

    data class PendingAnchoringItem(
        val receiptId: String,
        val retryCount: Long,
        val status: String
    )

    suspend fun getPendingAnchorings(): List<PendingAnchoringItem>
    suspend fun deletePendingAnchoring(receiptId: String)
    suspend fun updatePendingAnchoringStatus(receiptId: String, status: String, retryCount: Long, lastAttemptAt: Long)

    // ── Issuance queue ──

    data class PendingIssuanceItem(
        val id: String,
        val clientId: String,
        val credentialTypesJson: String,
        val format: String,
        val sessionId: String?,
        val accessToken: String,
        val tokenExpiresAt: Long,
        val keyAlias: String?,
        val holderJwk: String?,
        val retryCount: Long,
        val status: String
    )

    suspend fun deleteExpiredIssuances(nowMillis: Long)
    suspend fun getPendingIssuances(): List<PendingIssuanceItem>
    suspend fun deletePendingIssuance(id: String)
    suspend fun updatePendingIssuanceStatus(id: String, status: String, retryCount: Long, lastAttemptAt: Long)

    // ── Counts ──

    suspend fun getPendingAnchoringCount(): Int
    suspend fun getPendingIssuanceCount(): Int
}
