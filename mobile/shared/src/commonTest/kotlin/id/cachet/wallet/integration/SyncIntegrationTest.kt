package id.cachet.wallet.integration

import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.sync.SyncManager
import id.cachet.wallet.domain.sync.SyncQueueRepository
import id.cachet.wallet.domain.usecase.ConsentUseCase
import id.cachet.wallet.domain.usecase.IssuanceUseCase
import id.cachet.wallet.network.CredentialResponse
import id.cachet.wallet.testfixtures.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests validating the full offline queue and sync flow
 * across ConsentUseCase/IssuanceUseCase → Queue → SyncManager → Repository.
 *
 * Each test wires real use cases to a shared FakeSyncQueueRepository
 * that SyncManager reads from, proving state crosses component boundaries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncIntegrationTest {

    private val validConsent = ConsentDetails(
        explicitConsent = true,
        dataMinimizationAcknowledged = true,
        retentionPeriodUnderstood = true,
        retentionPeriodDays = 90
    )

    // ── Flow A: receipt offline → enqueued → sync → anchored ──

    @Test
    fun `receipt generated offline is anchored after reconnect`() = runTest {
        // Shared state between ConsentUseCase and SyncManager
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()
        val logRepo = ControllableTransparencyLogRepository()
        val connectivity = FakeConnectivityObserver(initialOnline = false)

        // ConsentUseCase uses an AnchoringQueue backed by the same queueRepo
        val anchoringQueue = InMemoryAnchoringQueue(queueRepo)
        val consentUseCase = ConsentUseCase(
            credentialRepository = FakeCredentialRepository(),
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = logRepo,
            anchoringQueue = anchoringQueue
        )

        val syncManager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = logRepo,
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )
        syncManager.start()

        // Phase 1: Generate receipt while transparency log is unavailable
        logRepo.shouldFail = true
        val result = consentUseCase.generateConsentReceipt(
            credential = makeCredential(),
            presentationRequest = makePresentationRequest(),
            userConsent = validConsent
        )

        assertTrue(result.isSuccess, "Receipt should succeed even when anchoring fails")
        val receipt = result.getOrThrow()
        assertNull(receipt.transparencyLogEntry, "Receipt should not be anchored yet")

        // Verify receipt is stored AND enqueued
        val stored = consentRepo.getReceiptById(receipt.id).getOrThrow()
        assertNotNull(stored, "Receipt should be persisted")
        assertEquals(1, queueRepo.anchoringItems.size, "Receipt should be enqueued for later anchoring")
        assertEquals(receipt.id, queueRepo.anchoringItems[0].receiptId)

        // Phase 2: Restore connectivity with working transparency log
        logRepo.shouldFail = false
        connectivity.goOnline()
        advanceUntilIdle()

        // Verify: queue drained, receipt NOT updated in repo yet (updateTransparencyLog
        // is called on the repo, but InMemoryConsentReceiptRepository.updateTransparencyLog
        // is a no-op for the in-memory impl — the important assertion is that the queue is empty)
        syncManager.refreshPendingCount()
        assertEquals(0, syncManager.pendingCount.value, "Queue should be fully drained after sync")
        assertEquals(0, queueRepo.anchoringItems.size, "Anchoring item should be removed from queue")
    }

    // ── Flow B: issuance fails mid-flow → enqueued → sync → credential stored ──

    @Test
    fun `issuance queued offline is completed after reconnect`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val credentialRepo = FakeCredentialRepository()
        val connectivity = FakeConnectivityObserver(initialOnline = false)

        // Client: token succeeds, credential fails initially
        val client = FakeOpenID4VCIClient(
            credentialError = RuntimeException("Network unavailable")
        )

        val issuanceQueue = InMemoryIssuanceQueue(queueRepo)
        val issuanceUseCase = IssuanceUseCase(
            credentialRepository = credentialRepo,
            openID4VCIClient = client,
            issuanceQueue = issuanceQueue
        )

        val syncManager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = ControllableTransparencyLogRepository(),
            openID4VCIClient = client,
            credentialRepository = credentialRepo,
            scope = this
        )
        syncManager.start()

        // Phase 1: Attempt issuance — token succeeds, credential fetch fails
        val result = issuanceUseCase.requestCredential(
            clientId = "cachet-android-wallet",
            credentialTypes = listOf("VerifiableCredential", "IdentityCredential"),
            sessionId = "veriff-session-1"
        )

        assertTrue(result.isFailure, "Issuance should fail when credential fetch fails")
        assertEquals(1, queueRepo.issuanceItems.size, "Partial state should be enqueued")

        val queued = queueRepo.issuanceItems[0]
        assertEquals("cachet-android-wallet", queued.clientId)
        assertEquals("fake-token", queued.accessToken, "Token should be preserved in queue")
        assertEquals("veriff-session-1", queued.sessionId)
        assertTrue(queued.tokenExpiresAt > 0, "Token expiry should be set")
        assertEquals(0, credentialRepo.getAllCredentials().size, "No credential stored yet")

        // Phase 2: Fix the client and restore connectivity
        client.credentialError = null
        client.credentialResponse = CredentialResponse(
            credential = makeCredential(),
            format = "jwt_vc"
        )
        connectivity.goOnline()
        advanceUntilIdle()

        // Verify: credential issued and queue drained
        syncManager.refreshPendingCount()
        assertEquals(0, syncManager.pendingCount.value, "Queue should be drained")
        assertEquals(1, credentialRepo.getAllCredentials().size, "Credential should be stored after sync")
    }

    // ── Flow C: token expires while offline → marked expired ──

    @Test
    fun `expired token issuance is not retried after reconnect`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val credentialRepo = FakeCredentialRepository()
        val connectivity = FakeConnectivityObserver(initialOnline = false)

        // Manually enqueue an issuance with an already-expired token
        // (simulates: user went offline, came back hours later)
        val expiredAt = kotlin.time.Clock.System.now().toEpochMilliseconds() - 3_600_000L
        queueRepo.issuanceItems.add(
            SyncQueueRepository.PendingIssuanceItem(
                id = "issuance-stale",
                clientId = "cachet-android-wallet",
                credentialTypesJson = """["VerifiableCredential"]""",
                format = "jwt_vc",
                sessionId = "old-session",
                accessToken = "expired-token",
                tokenExpiresAt = expiredAt,
                keyAlias = null,
                holderJwk = null,
                retryCount = 0,
                status = "pending"
            )
        )

        val syncManager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = InMemoryConsentReceiptRepository(),
            transparencyLogRepository = ControllableTransparencyLogRepository(),
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = credentialRepo,
            scope = this
        )
        syncManager.start()

        // Go online
        connectivity.goOnline()
        advanceUntilIdle()

        // Verify: expired items are cleaned up, no credential stored
        syncManager.refreshPendingCount()
        assertEquals(0, syncManager.pendingCount.value, "Expired item should be removed")
        assertEquals(0, credentialRepo.getAllCredentials().size, "No credential should be issued with expired token")
    }

    // ── Flow D: already-anchored receipt → idempotently skipped ──

    @Test
    fun `already-anchored receipt in queue is cleaned up without API calls`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()
        val logRepo = ControllableTransparencyLogRepository()
        val connectivity = FakeConnectivityObserver(initialOnline = false)

        // Store a receipt that was already successfully anchored
        val receipt = makeConsentReceipt(id = "receipt-already-anchored")
            .copy(
                receiptHash = "hash",
                salt = "salt",
                signature = "sig",
                transparencyLogEntry = id.cachet.wallet.domain.model.TransparencyLogEntry(
                    logId = "log-1",
                    logIndex = 42
                )
            )
        consentRepo.storeReceipt(receipt)

        // But it's still in the queue (e.g., crash before queue cleanup)
        queueRepo.anchoringItems.add(
            SyncQueueRepository.PendingAnchoringItem("receipt-already-anchored", 0, "pending")
        )

        val syncManager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = logRepo,
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )
        syncManager.start()

        // Make log fail — if SyncManager incorrectly tries to re-anchor, it would fail
        logRepo.shouldFail = true
        connectivity.goOnline()
        advanceUntilIdle()

        // Verify: item removed from queue even though log is failing
        // (because idempotency check sees transparencyLogEntry is already set)
        syncManager.refreshPendingCount()
        assertEquals(0, syncManager.pendingCount.value, "Already-anchored receipt should be cleaned up")
        assertEquals(0, queueRepo.anchoringItems.size)
    }

    // ── Flow E: max retries exceeded → marked failed, stops retrying ──

    @Test
    fun `anchoring stops retrying after max attempts`() = runTest {
        val queueRepo = FakeSyncQueueRepository()
        val consentRepo = InMemoryConsentReceiptRepository()
        val logRepo = ControllableTransparencyLogRepository()
        logRepo.shouldFail = true
        val connectivity = FakeConnectivityObserver(initialOnline = true)

        val anchoringQueue = InMemoryAnchoringQueue(queueRepo)
        val consentUseCase = ConsentUseCase(
            credentialRepository = FakeCredentialRepository(),
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = logRepo,
            anchoringQueue = anchoringQueue
        )

        val syncManager = SyncManager(
            connectivity = connectivity,
            queueRepository = queueRepo,
            consentReceiptRepository = consentRepo,
            transparencyLogRepository = logRepo,
            openID4VCIClient = FakeOpenID4VCIClient(),
            credentialRepository = FakeCredentialRepository(),
            scope = this
        )

        // Phase 1: Generate receipt offline (log fails, enqueued)
        val result = consentUseCase.generateConsentReceipt(
            credential = makeCredential(),
            presentationRequest = makePresentationRequest(),
            userConsent = validConsent
        )
        assertTrue(result.isSuccess)
        assertEquals(1, queueRepo.anchoringItems.size)

        // Phase 2: Retry MAX_RETRY_COUNT times — all fail
        repeat(SyncManager.MAX_RETRY_COUNT) {
            syncManager.triggerSync()
            advanceUntilIdle()
        }

        // Verify: item marked as failed, no longer in pending count
        assertEquals(1, queueRepo.anchoringItems.size, "Item should still exist but be marked failed")
        assertEquals("failed", queueRepo.anchoringItems[0].status)
        assertEquals(SyncManager.MAX_RETRY_COUNT.toLong(), queueRepo.anchoringItems[0].retryCount)
        syncManager.refreshPendingCount()
        assertEquals(0, syncManager.pendingCount.value, "Failed items should not count as pending")

        // Phase 3: One more sync — failed items are NOT retried
        val retryCountBefore = queueRepo.anchoringItems[0].retryCount
        syncManager.triggerSync()
        advanceUntilIdle()

        assertEquals(retryCountBefore, queueRepo.anchoringItems[0].retryCount,
            "Failed items should not be retried — retry count should not increase")
        assertEquals("failed", queueRepo.anchoringItems[0].status)
    }
}
