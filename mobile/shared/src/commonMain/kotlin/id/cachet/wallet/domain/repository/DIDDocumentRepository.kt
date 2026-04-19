package id.cachet.wallet.domain.repository

data class CachedDIDDocument(
    val did: String,
    val documentJson: String,
    val fetchedAt: Long
)

interface DIDDocumentRepository {
    suspend fun getDocument(did: String): Result<CachedDIDDocument?>
    suspend fun storeDocument(did: String, documentJson: String, fetchedAt: Long): Result<Unit>
    suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit>
}
