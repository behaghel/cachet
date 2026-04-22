package id.cachet.wallet.domain.sync

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.testfixtures.FakeConnectivityObserver
import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.FakeOpenID4VCIClient
import id.cachet.wallet.testfixtures.FakeSyncQueueRepository
import id.cachet.wallet.testfixtures.makeCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    /** Transparency log that always fails. */
    private class FailingLogRepository : TransparencyLogRepository {
        override suspend fun submitReceiptHash(request: AddEntryRequest) =
            Result.failure<AddEntryResponse>(RuntimeException("Network error"))
        override suspend fun getCurrentSTH() =
            Result.failure<SignedTreeHead>(RuntimeException("Network error"))
        override suspend fun getEntries(start: Long, end: Long) =
            Result.failure<List<LogEntry>>(RuntimeException("Network error"))
        override suspend fun getInclusionProof(leafIndex: Long, treeSize: Long) =
            Result.failure<InclusionProof>(RuntimeException("Network error"))
        override suspend fun getConsistencyProof(firstTreeSize: Long, secondTreeSize: Long) =
            Result.failure<ConsistencyProof>(RuntimeException("Network error"))
        override fun verifyInclusionProof(leafHash: String, proof: InclusionProof) = false
        override fun verifyConsistencyProof(firstSTH: SignedTreeHead, secondSTH: SignedTreeHead, proof: ConsistencyProof) = false
    }

    private fun makeReceipt(id: String, anchored: Boolean = false) = ConsentReceipt(
        id = id,
        timestamp = Clock.System.now(),
        purpose = "Test verification purpose",
        predicatesProven = listOf("age_gte_18"),
        rpIdentifier = "did:web:test.com",
        rpDisplayName = "Test Verifier",
        userConsent = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true
        ),
        credentialId = "cred-1",
        receiptHash = "abc123hash",
        salt = "test-salt",
        signature = "test-sig",
        transparencyLogEntry = if (anchored) TransparencyLogEntry(logId = "log", logIndex = 42) else null
    )

    @Test
    fun `pendingCount starts at zero`() = runTest {
        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(),
            queueRepository = FakeSyncQueueRepository(),
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        assertEquals(0, manager.pendingCount.value)
    }

    @Test
    fun `pendingCount reflects enqueued items`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        queueRepo.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem("receipt-1", 0, "pending")
        )

        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(),
            queueRepository = queueRepo,
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        manager.refreshPendingCount()
        assertEquals(1, manager.pendingCount.value)
    }

    @Test
    fun `drains anchoring queue on connectivity restore`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()
        val connectivity = FakeConnectivityObserver(initialOnline = false)

        consentRepo.storeReceipt(makeReceipt("receipt-1"))
        queueRepo.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem("receipt-1", 0, "pending")
        )

        val manager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        manager.start()
        advanceUntilIdle()

        // Still pending (offline)
        manager.refreshPendingCount()
        assertEquals(1, manager.pendingCount.value)

        // Go online → triggers drain
        connectivity.goOnline()
        advanceUntilIdle()

        // Queue should be drained
        manager.refreshPendingCount()
        assertEquals(0, manager.pendingCount.value)
    }

    @Test
    fun `skips already-anchored receipts as idempotent`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()

        consentRepo.storeReceipt(makeReceipt("receipt-already", anchored = true))
        queueRepo.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem("receipt-already", 0, "pending")
        )

        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(initialOnline = true),
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        manager.triggerSync()
        advanceUntilIdle()

        manager.refreshPendingCount()
        assertEquals(0, manager.pendingCount.value)
    }

    @Test
    fun `marks anchoring as failed after max retries`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()

        consentRepo.storeReceipt(makeReceipt("receipt-retry"))
        queueRepo.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem(
                "receipt-retry",
                (SyncManager.MAX_RETRY_COUNT - 1).toLong(),
                "pending"
            )
        )

        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(initialOnline = true),
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = FailingLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        manager.triggerSync()
        advanceUntilIdle()

        assertEquals(1, queueRepo.anchoringItems.size)
        assertEquals("failed", queueRepo.anchoringItems[0].status)
        assertEquals(SyncManager.MAX_RETRY_COUNT.toLong(), queueRepo.anchoringItems[0].retryCount)
    }

    @Test
    fun `marks issuance as expired when token is past expiry`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val expiredAt = Clock.System.now().toEpochMilliseconds() - 3_600_000L

        queueRepo.issuanceItems.add(
            SyncQueueRepository.PendingIssuanceItem(
                id = "issuance-expired",
                clientId = "test",
                credentialTypesJson = """["VerifiableCredential"]""",
                format = "jwt_vc",
                sessionId = null,
                accessToken = "expired-token",
                tokenExpiresAt = expiredAt,
                keyAlias = null,
                holderJwk = null,
                retryCount = 0,
                status = "pending"
            )
        )

        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(initialOnline = true),
            queueRepository = queueRepo,
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        manager.triggerSync()
        advanceUntilIdle()

        // Expired items get deleted by deleteExpiredIssuances
        manager.refreshPendingCount()
        assertEquals(0, manager.pendingCount.value)
    }

    @Test
    fun `resumes issuance with valid token`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val credentialRepo = FakeCredentialRepository()
        val client = FakeOpenID4VCIClient(
            credentialResponse = CredentialResponse(
                credential = makeCredential(),
                format = "jwt_vc"
            )
        )

        val expiresAt = Clock.System.now().toEpochMilliseconds() + 3_600_000L
        queueRepo.issuanceItems.add(
            SyncQueueRepository.PendingIssuanceItem(
                id = "issuance-valid",
                clientId = "test",
                credentialTypesJson = """["VerifiableCredential"]""",
                format = "jwt_vc",
                sessionId = null,
                accessToken = "valid-token",
                tokenExpiresAt = expiresAt,
                keyAlias = null,
                holderJwk = null,
                retryCount = 0,
                status = "pending"
            )
        )

        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(initialOnline = true),
            queueRepository = queueRepo,
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = client,
            credentialRepository = credentialRepo,
            scope = this
        )

        manager.triggerSync()
        advanceUntilIdle()

        manager.refreshPendingCount()
        assertEquals(0, manager.pendingCount.value)
        assertEquals(1, credentialRepo.getAllCredentials().size)
    }

    @Test
    fun `syncStatus transitions correctly`() = runTest {
        val manager = SyncManager(
            connectivity = FakeConnectivityObserver(initialOnline = true),
            queueRepository = FakeSyncQueueRepository(),
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = MockTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        assertEquals(SyncStatus.IDLE, manager.syncStatus.value)

        manager.triggerSync()
        advanceUntilIdle()

        assertEquals(SyncStatus.IDLE, manager.syncStatus.value)
    }
}
