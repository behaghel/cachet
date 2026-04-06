package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.makeCredential
import id.cachet.wallet.testfixtures.makePresentationRequest
import id.cachet.wallet.testfixtures.makeStoredCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsentUseCaseTest {

    private fun createUseCase(
        credRepo: FakeCredentialRepository = FakeCredentialRepository(),
        consentRepo: InMemoryConsentReceiptRepository = InMemoryConsentReceiptRepository(),
        logRepo: MockTransparencyLogRepository = MockTransparencyLogRepository()
    ) = ConsentUseCase(credRepo, consentRepo, logRepo)

    private val validConsent = ConsentDetails(
        explicitConsent = true,
        dataMinimizationAcknowledged = true,
        retentionPeriodUnderstood = true,
        retentionPeriodDays = 90
    )

    // ── validatePresentationRequest ──

    @Test
    fun `validatePresentationRequest returns true for valid request`() {
        val useCase = createUseCase()
        val request = makePresentationRequest()
        assertTrue(useCase.validatePresentationRequest(request))
    }

    @Test
    fun `validatePresentationRequest returns false for blank RP`() {
        val useCase = createUseCase()
        val request = makePresentationRequest(rpIdentifier = "")
        assertFalse(useCase.validatePresentationRequest(request))
    }

    @Test
    fun `validatePresentationRequest returns false for short purpose`() {
        val useCase = createUseCase()
        val request = makePresentationRequest(purpose = "short")
        assertFalse(useCase.validatePresentationRequest(request))
    }

    @Test
    fun `validatePresentationRequest returns false for empty predicates`() {
        val useCase = createUseCase()
        val request = makePresentationRequest(requestedPredicates = emptyList())
        assertFalse(useCase.validatePresentationRequest(request))
    }

    // ── generateConsentReceipt ──

    @Test
    fun `generateConsentReceipt creates and stores receipt`() = runTest {
        val consentRepo = InMemoryConsentReceiptRepository()
        val useCase = ConsentUseCase(FakeCredentialRepository(), consentRepo, MockTransparencyLogRepository())
        val cred = makeCredential()
        val request = makePresentationRequest()

        val result = useCase.generateConsentReceipt(cred, request, validConsent)

        assertTrue(result.isSuccess)
        val receipt = result.getOrThrow()
        assertNotNull(receipt.receiptHash)
        assertNotNull(receipt.salt)
        // Verify it was stored
        val stored = consentRepo.getAllReceipts().getOrThrow()
        assertEquals(1, stored.size)
        assertEquals(receipt.id, stored[0].id)
    }

    @Test
    fun `generateConsentReceipt anchors to transparency log`() = runTest {
        val useCase = createUseCase()
        val cred = makeCredential()
        val request = makePresentationRequest()

        val result = useCase.generateConsentReceipt(cred, request, validConsent)

        assertTrue(result.isSuccess)
        val receipt = result.getOrThrow()
        assertNotNull(receipt.transparencyLogEntry)
        assertNotNull(receipt.transparencyLogEntry!!.sct)
    }

    // ── presentCredential ──

    @Test
    fun `presentCredential succeeds for valid credential`() = runTest {
        val credRepo = FakeCredentialRepository()
        credRepo.storeCredential(makeStoredCredential(localId = "cred-1", credential = makeCredential(id = "cred-1")))
        val useCase = ConsentUseCase(credRepo, InMemoryConsentReceiptRepository(), MockTransparencyLogRepository())
        val request = makePresentationRequest(requestedPredicates = listOf("age_gte_18"))

        val result = useCase.presentCredential("cred-1", request, validConsent)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().success)
    }

    @Test
    fun `presentCredential fails for missing credential`() = runTest {
        val useCase = createUseCase()
        val request = makePresentationRequest()

        val result = useCase.presentCredential("nonexistent", request, validConsent)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().success)
        assertEquals("Credential not found", result.getOrThrow().errorMessage)
    }

    // ── getConsentReceipts ──

    @Test
    fun `getConsentReceipts returns stored receipts`() = runTest {
        val consentRepo = InMemoryConsentReceiptRepository()
        val useCase = ConsentUseCase(FakeCredentialRepository(), consentRepo, MockTransparencyLogRepository())
        val cred = makeCredential()

        useCase.generateConsentReceipt(cred, makePresentationRequest(), validConsent)
        useCase.generateConsentReceipt(cred, makePresentationRequest(rpIdentifier = "did:web:other.com"), validConsent)

        val result = useCase.getConsentReceipts()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }
}
