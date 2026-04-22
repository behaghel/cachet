package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.IssuanceQueue
import id.cachet.wallet.domain.sync.PendingIssuanceState
import id.cachet.wallet.domain.sync.SyncQueueRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * IssuanceQueue backed by FakeSyncQueueRepository —
 * bridges IssuanceUseCase enqueue calls to the same store SyncManager reads from.
 */
class InMemoryIssuanceQueue(
    private val queueRepository: FakeSyncQueueRepository
) : IssuanceQueue {

    override suspend fun enqueue(state: PendingIssuanceState) {
        queueRepository.issuanceItems.add(
            SyncQueueRepository.PendingIssuanceItem(
                id = state.id,
                clientId = state.clientId,
                credentialTypesJson = Json.encodeToString(state.credentialTypes),
                format = state.format,
                sessionId = state.sessionId,
                accessToken = state.accessToken,
                tokenExpiresAt = state.tokenExpiresAt,
                keyAlias = state.keyAlias,
                holderJwk = state.holderJwk,
                retryCount = 0,
                status = "pending"
            )
        )
    }
}
