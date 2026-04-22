package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository

/**
 * Transparency log repository that can be toggled between failing and succeeding.
 * When [shouldFail] is true, submitReceiptHash returns failure; otherwise delegates to mock.
 */
class ControllableTransparencyLogRepository : TransparencyLogRepository {

    var shouldFail: Boolean = false

    private val delegate = MockTransparencyLogRepository()

    override suspend fun submitReceiptHash(request: AddEntryRequest): Result<AddEntryResponse> {
        if (shouldFail) return Result.failure(RuntimeException("Network unavailable"))
        return delegate.submitReceiptHash(request)
    }

    override suspend fun getCurrentSTH(): Result<SignedTreeHead> {
        if (shouldFail) return Result.failure(RuntimeException("Network unavailable"))
        return delegate.getCurrentSTH()
    }

    override suspend fun getEntries(start: Long, end: Long): Result<List<LogEntry>> {
        if (shouldFail) return Result.failure(RuntimeException("Network unavailable"))
        return delegate.getEntries(start, end)
    }

    override suspend fun getInclusionProof(leafIndex: Long, treeSize: Long): Result<InclusionProof> {
        if (shouldFail) return Result.failure(RuntimeException("Network unavailable"))
        return delegate.getInclusionProof(leafIndex, treeSize)
    }

    override suspend fun getConsistencyProof(firstTreeSize: Long, secondTreeSize: Long): Result<ConsistencyProof> {
        if (shouldFail) return Result.failure(RuntimeException("Network unavailable"))
        return delegate.getConsistencyProof(firstTreeSize, secondTreeSize)
    }

    override fun verifyInclusionProof(leafHash: String, proof: InclusionProof): Boolean =
        delegate.verifyInclusionProof(leafHash, proof)

    override fun verifyConsistencyProof(firstSTH: SignedTreeHead, secondSTH: SignedTreeHead, proof: ConsistencyProof): Boolean =
        delegate.verifyConsistencyProof(firstSTH, secondSTH, proof)
}
