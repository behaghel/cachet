package id.cachet.wallet.android.data

import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.model.VerifiableCredential
import id.cachet.wallet.domain.repository.CredentialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class CredentialRepositoryImpl(
    private val database: WalletDatabase
) : CredentialRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun storeCredential(credential: StoredCredential) {
        withContext(Dispatchers.IO) {
            val credentialJson = json.encodeToString(credential.credential)
            
            database.walletDatabaseQueries.insertCredential(
                local_id = credential.localId,
                credential_id = credential.credential.id,
                issuer = credential.credential.issuer,
                credential_json = credentialJson,
                raw_jwt = credential.rawJwt,
                created_at = credential.createdAt.epochSeconds,
                is_revoked = if (credential.isRevoked) 1L else 0L
            )
        }
    }
    
    override suspend fun getAllCredentials(): List<StoredCredential> {
        return withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.getAllCredentials()
                .executeAsList()
                .map { row ->
                    val credential = json.decodeFromString<VerifiableCredential>(row.credential_json)
                    StoredCredential(
                        localId = row.local_id,
                        credential = credential,
                        rawJwt = row.raw_jwt,
                        createdAt = Instant.fromEpochSeconds(row.created_at),
                        isRevoked = row.is_revoked == 1L
                    )
                }
        }
    }
    
    override suspend fun getCredentialById(localId: String): StoredCredential? {
        return withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.getCredentialById(localId)
                .executeAsOneOrNull()
                ?.let { row ->
                    val credential = json.decodeFromString<VerifiableCredential>(row.credential_json)
                    StoredCredential(
                        localId = row.local_id,
                        credential = credential,
                        rawJwt = row.raw_jwt,
                        createdAt = Instant.fromEpochSeconds(row.created_at),
                        isRevoked = row.is_revoked == 1L
                    )
                }
        }
    }
    
    override suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential> {
        return withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.getCredentialsByIssuer(issuer)
                .executeAsList()
                .map { row ->
                    val credential = json.decodeFromString<VerifiableCredential>(row.credential_json)
                    StoredCredential(
                        localId = row.local_id,
                        credential = credential,
                        rawJwt = row.raw_jwt,
                        createdAt = Instant.fromEpochSeconds(row.created_at),
                        isRevoked = row.is_revoked == 1L
                    )
                }
        }
    }
    
    override suspend fun markCredentialRevoked(localId: String) {
        withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.markCredentialRevoked(localId)
        }
    }
    
    override suspend fun deleteCredential(localId: String) {
        withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.deleteCredential(localId)
        }
    }
}