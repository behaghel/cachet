package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.testfixtures.FakeAnchoringQueue
import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.makeCredential
import id.cachet.wallet.testfixtures.makePresentationRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsentUseCaseOfflineTest {

    private val validConsent = ConsentDetails(
        explicitConsent = true,
        dataMinimizationAcknowledged = true,
        retentionPeriodUnderstood = true,
        retentionPeriodDays = 90
    )

    /** Transparency log that always fails on submitReceiptHash. */
    private class FailingTransparencyLogRepository : TransparencyLogRepository {
        override suspend fun submitReceiptHash(request: AddEntryRequest): Result<AddEntryResponse> =
            Result.failure(RuntimeException("Network unavailable"))

        override suspend fun getCurrentSTH(): Result<SignedTreeHead> =
            Result.failure(RuntimeException("Network unavailable"))

        override suspend fun getEntries(start: Long, end: Long): Result<List<LogEntry>> =
            Result.failure(RuntimeException("Network unavailable"))

        override suspend fun getInclusionProof(leafIndex: Long, treeSize: Long): Result<InclusionProof> =
            Result.failure(RuntimeException("Network unavailable"))

        override suspend fun getConsistencyProof(firstTreeSize: Long, secondTreeSize: Long): Result<ConsistencyProof> =
            Result.failure(RuntimeException("Network unavailable"))

        override fun verifyInclusionProof(leafHash: String, proof: InclusionProof): Boolean = false
        override fun verifyConsistencyProof(firstSTH: SignedTreeHead, secondSTH: SignedTreeHead, proof: ConsistencyProof): Boolean = false
    }

    @Test
    fun `generateConsentReceipt enqueues when transparency log fails`() = runTest {
        val queue = FakeAnchoringQueue()
        val useCase = ConsentUseCase(
            credentialRepository = FakeCredentialRepository(),
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = FailingTransparencyLogRepository(),
            anchoringQueue = queue
        )

        val result = useCase.generateConsentReceipt(makeCredential(), makePresentationRequest(), validConsent)

        assertTrue(result.isSuccess, "Receipt should still succeed even when anchoring fails")
        assertEquals(1, queue.enqueuedReceiptIds.size, "Receipt should be enqueued for later anchoring")
        assertEquals(result.getOrThrow().id, queue.enqueuedReceiptIds[0])
    }

    @Test
    fun `generateConsentReceipt does not enqueue when transparency log succeeds`() = runTest {
        val queue = FakeAnchoringQueue()
        val useCase = ConsentUseCase(
            credentialRepository = FakeCredentialRepository(),
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            anchoringQueue = queue
        )

        val result = useCase.generateConsentReceipt(makeCredential(), makePresentationRequest(), validConsent)

        assertTrue(result.isSuccess)
        assertEquals(0, queue.enqueuedReceiptIds.size, "No enqueue needed when anchoring succeeds")
    }

    @Test
    fun `generateConsentReceipt does not crash when no queue configured`() = runTest {
        val useCase = ConsentUseCase(
            credentialRepository = FakeCredentialRepository(),
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = FailingTransparencyLogRepository(),
            anchoringQueue = null
        )

        val result = useCase.generateConsentReceipt(makeCredential(), makePresentationRequest(), validConsent)

        assertTrue(result.isSuccess, "Should succeed even without queue (backward compat)")
    }
}
