package id.cachet.wallet.domain.sync

import id.cachet.wallet.db.WalletDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class SqlDelightIssuanceQueue(
    private val database: WalletDatabase,
    private val clock: Clock = Clock.System
) : IssuanceQueue {

    override suspend fun enqueue(state: PendingIssuanceState) {
        database.walletDatabaseQueries.insertPendingIssuance(
            id = state.id,
            client_id = state.clientId,
            credential_types_json = Json.encodeToString(state.credentialTypes),
            format = state.format,
            session_id = state.sessionId,
            access_token = state.accessToken,
            token_expires_at = state.tokenExpiresAt,
            key_alias = state.keyAlias,
            holder_jwk = state.holderJwk,
            created_at = clock.now().toEpochMilliseconds(),
            retry_count = 0,
            last_attempt_at = null,
            status = "pending"
        )
    }
}
