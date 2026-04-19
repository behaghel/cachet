package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.repository.CachedDIDDocument
import id.cachet.wallet.domain.repository.DIDDocumentRepository

class FakeDIDDocumentRepository : DIDDocumentRepository {
    private val documents = mutableMapOf<String, CachedDIDDocument>()

    override suspend fun getDocument(did: String): Result<CachedDIDDocument?> {
        return Result.success(documents[did])
    }

    override suspend fun storeDocument(did: String, documentJson: String, fetchedAt: Long): Result<Unit> {
        documents[did] = CachedDIDDocument(did, documentJson, fetchedAt)
        return Result.success(Unit)
    }

    override suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit> {
        documents.entries.removeAll { it.value.fetchedAt < cutoffEpochMs }
        return Result.success(Unit)
    }
}
