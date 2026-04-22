package id.cachet.wallet.domain.sync

/**
 * State persisted when credential issuance fails after token was obtained.
 * Enables retry on reconnect without re-authenticating.
 */
data class PendingIssuanceState(
    val id: String,
    val clientId: String,
    val credentialTypes: List<String>,
    val format: String,
    val sessionId: String?,
    val accessToken: String,
    val tokenExpiresAt: Long,
    val keyAlias: String?,
    val holderJwk: String?
)

/**
 * Queue for partially-completed issuance flows.
 * Items are enqueued when credential fetch fails after OAuth token was obtained,
 * and drained by SyncManager when connectivity is restored.
 */
interface IssuanceQueue {
    suspend fun enqueue(state: PendingIssuanceState)
}
