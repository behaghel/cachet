package id.cachet.wallet.domain.repository

import id.cachet.wallet.db.WalletDatabase

class SqlDelightDIDDocumentRepository(
    private val database: WalletDatabase
) : DIDDocumentRepository {

    override suspend fun getDocument(did: String): Result<CachedDIDDocument?> {
        return try {
            val row = database.walletDatabaseQueries.getDidDocument(did).executeAsOneOrNull()
            Result.success(row?.let {
                CachedDIDDocument(
                    did = it.did,
                    documentJson = it.document_json,
                    fetchedAt = it.fetched_at
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun storeDocument(did: String, documentJson: String, fetchedAt: Long): Result<Unit> {
        return try {
            database.walletDatabaseQueries.upsertDidDocument(
                did = did,
                document_json = documentJson,
                fetched_at = fetchedAt
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit> {
        return try {
            database.walletDatabaseQueries.deleteExpiredDidDocuments(cutoffEpochMs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
