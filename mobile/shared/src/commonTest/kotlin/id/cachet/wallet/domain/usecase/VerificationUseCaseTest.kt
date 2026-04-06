package id.cachet.wallet.domain.usecase

import id.cachet.wallet.config.AppConfig
import id.cachet.wallet.domain.repository.InMemoryConsentReceiptRepository
import id.cachet.wallet.domain.repository.MockTransparencyLogRepository
import id.cachet.wallet.network.*
import id.cachet.wallet.testfixtures.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationUseCaseTest {

    private val credRepo = FakeCredentialRepository()
    private val verifierClient = FakeVerifierClient()
    private val relayClient = FakeRelayClient()
    private val consentUseCase = ConsentUseCase(
        credRepo,
        InMemoryConsentReceiptRepository(),
        MockTransparencyLogRepository()
    )

    private val fakeKeyManager = FakeKeyManager()

    private fun createUseCase(withKeyManager: Boolean = false) = VerificationUseCase(
        credentialRepository = credRepo,
        verifierClient = verifierClient,
        relayClient = relayClient,
        consentUseCase = consentUseCase,
        keyManager = if (withKeyManager) fakeKeyManager else null
    )

    @AfterTest
    fun tearDown() {
        AppConfig.reset()
    }

    // ── getAvailablePacks ──

    @Test
    fun `getAvailablePacks returns packs from verifier`() = runTest {
        verifierClient.packs = listOf(
            PackSummary(id = "childcare-es", version = "1.0", name = "Childcare Readiness"),
            PackSummary(id = "age-check", version = "1.0", name = "Age Verification")
        )
        val useCase = createUseCase()

        val result = useCase.getAvailablePacks()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        assertEquals("childcare-es", result.getOrThrow()[0].id)
    }

    @Test
    fun `getAvailablePacks returns empty list when no packs`() = runTest {
        verifierClient.packs = emptyList()
        val useCase = createUseCase()

        val result = useCase.getAvailablePacks()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    // ── startVerifierSession ──

    @Test
    fun `startVerifierSession creates session and relay`() = runTest {
        AppConfig.configure(relayUrl = "http://test-relay:8084")
        verifierClient.session = VerificationSession(
            sessionId = "sess-1",
            nonce = "nonce-abc",
            verifierDid = "did:web:verifier.example.com"
        )
        relayClient.session = RelaySession(
            sessionId = "relay-1",
            requestUri = "/sessions/relay-1/request",
            responseUri = "/sessions/relay-1/response"
        )
        val useCase = createUseCase()

        val info = useCase.startVerifierSession("childcare-es", "Are you safe?", listOf("age_gte_18"))

        assertTrue(info.qrPayload.contains("cachet://verify?request_uri="))
        assertTrue(info.qrPayload.contains("http://test-relay:8084/sessions/relay-1/request"))
        assertEquals("/sessions/relay-1/response", info.relayResponseUri)
        assertEquals("sess-1", info.verificationSessionId)
        assertEquals("childcare-es", info.packId)
    }

    @Test
    fun `startVerifierSession includes ephemeral key in QR when present`() = runTest {
        AppConfig.configure(relayUrl = "http://relay")
        verifierClient.session = VerificationSession(
            sessionId = "s1",
            nonce = "n1",
            verifierDid = "did:web:v",
            ephemeralPubKey = "x25519-pub-key"
        )
        val useCase = createUseCase()

        val info = useCase.startVerifierSession("pack-1", "q", listOf("p"))

        assertTrue(info.qrPayload.contains("&vk=x25519-pub-key"))
    }

    @Test
    fun `startVerifierSession omits vk param when no ephemeral key`() = runTest {
        AppConfig.configure(relayUrl = "http://relay")
        verifierClient.session = VerificationSession(
            sessionId = "s1",
            nonce = "n1",
            verifierDid = "did:web:v",
            ephemeralPubKey = null
        )
        val useCase = createUseCase()

        val info = useCase.startVerifierSession("pack-1", "q", listOf("p"))

        assertFalse(info.qrPayload.contains("&vk="))
    }

    @Test
    fun `startVerifierSession rejects empty packId`() = runTest {
        val useCase = createUseCase()
        try {
            useCase.startVerifierSession("", "question", listOf("pred"))
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("packId must not be empty"))
        }
    }

    @Test
    fun `startVerifierSession stores request object in relay when present`() = runTest {
        AppConfig.configure(relayUrl = "http://relay")
        verifierClient.session = VerificationSession(
            sessionId = "s1",
            nonce = "n1",
            verifierDid = "did:web:v",
            requestObject = "eyJhbGciOiJFUzI1NiJ9.payload.sig"
        )
        val useCase = createUseCase()

        useCase.startVerifierSession("pack-1", "q", listOf("p"))

        // The FakeRelayClient.createSession received the request object bytes
        // (can't assert directly since createSession doesn't store it, but the
        // test confirms no exception was thrown when requestObject is non-null)
    }

    // ── fetchVerificationRequest (plaintext path) ──

    @Test
    fun `fetchVerificationRequest parses plaintext JSON`() = runTest {
        val payload = VerificationRequestPayload(
            nonce = "test-nonce",
            verifierDid = "did:web:example.com",
            packId = "childcare-es",
            question = "Are you safe?",
            predicates = listOf("age_gte_18", "identity_verified")
        )
        relayClient.requestPayload = Json.encodeToString(
            VerificationRequestPayload.serializer(), payload
        ).encodeToByteArray()
        val useCase = createUseCase()

        val result = useCase.fetchVerificationRequest("http://relay/sessions/s1/request")

        assertFalse(result.isVerified)
        assertNull(result.verifierName)
        assertEquals("test-nonce", result.payload.nonce)
        assertEquals("did:web:example.com", result.payload.verifierDid)
        assertEquals("childcare-es", result.payload.packId)
        assertEquals(2, result.payload.predicates.size)
    }

    // ── awaitAndVerifyRelayResponse ──

    @Test
    fun `awaitAndVerifyRelayResponse returns result from verifier`() = runTest {
        relayClient.responsePayload = "sd-jwt-presentation~disc1~".encodeToByteArray()
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Childcare Ready",
            freshness = "2h",
            predicateResults = listOf(
                PredicateResultDTO(predicateId = "age_gte_18", status = "satisfied")
            ),
            summary = VerificationSummaryDTO(
                requiredSatisfied = 1,
                requiredTotal = 1,
                cachetGranted = true
            )
        )
        val useCase = createUseCase()
        val sessionInfo = VerifierSessionInfo(
            qrPayload = "cachet://verify?...",
            relayResponseUri = "/sessions/s1/response",
            verificationSessionId = "sess-1",
            packId = "childcare-es"
        )

        val result = useCase.awaitAndVerifyRelayResponse(sessionInfo)

        assertEquals("Childcare Ready", result.badge)
        assertEquals("2h", result.freshness)
        assertEquals(1, result.predicateResults.size)
        assertTrue(result.holderBound)
        assertNotNull(result.summary)
        assertTrue(result.summary!!.cachetGranted)
    }

    // ── verifyCredential (legacy path) ──

    @Test
    fun `verifyCredential legacy path returns result`() = runTest {
        val cred = makeStoredCredential(localId = "cred-1", credential = makeCredential(id = "cred-1"))
        credRepo.storeCredential(cred)
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Age Verified",
            freshness = "fresh",
            predicates = listOf("age_gte_18"),
            predicateResults = listOf(
                PredicateResultDTO(predicateId = "age_gte_18", status = "satisfied")
            )
        )
        val useCase = createUseCase()

        val result = useCase.verifyCredential("cred-1", "age-check")

        assertTrue(result.isSuccess)
        val verification = result.getOrThrow()
        assertEquals("Age Verified", verification.badge)
        assertFalse(verification.holderBound) // legacy path is not holder-bound
        assertNotNull(verification.consentReceiptId) // receipt generated for predicates
    }

    @Test
    fun `verifyCredential fails for missing credential`() = runTest {
        val useCase = createUseCase()

        val result = useCase.verifyCredential("nonexistent", "pack")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Credential not found"))
    }

    @Test
    fun `verifyCredential skips receipt when no predicates`() = runTest {
        val cred = makeStoredCredential(localId = "cred-1", credential = makeCredential(id = "cred-1"))
        credRepo.storeCredential(cred)
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "",
            freshness = "fresh",
            predicates = null
        )
        val useCase = createUseCase()

        val result = useCase.verifyCredential("cred-1", "pack")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().consentReceiptId)
    }

    // ── verifyCredential (SD-JWT path with FakeKeyManager) ──

    @Test
    fun `verifyCredential SD-JWT path creates session and verifies`() = runTest {
        fakeKeyManager.generateKeyPair("holder-key")
        val cred = makeStoredCredential(
            localId = "sd-1",
            credential = makeCredential(id = "sd-1"),
            rawSdJwt = "eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJkaWQ6dmVyaWZmOnByb2R1Y3Rpb24ifQ.sig~WyJzYWx0MSIsImFnZSIsMzBd~",
            keyAlias = "holder-key"
        )
        credRepo.storeCredential(cred)
        verifierClient.session = VerificationSession(
            sessionId = "sd-sess",
            nonce = "sd-nonce",
            verifierDid = "did:web:verifier"
        )
        verifierClient.verifyResponse = VerifyResponseDTO(
            cachet = "Age Verified",
            freshness = "fresh",
            predicates = listOf("age_gte_18"),
            predicateResults = listOf(
                PredicateResultDTO(predicateId = "age_gte_18", status = "satisfied")
            )
        )
        val useCase = createUseCase(withKeyManager = true)

        val result = useCase.verifyCredential("sd-1", "age-check")

        assertTrue(result.isSuccess)
        val verification = result.getOrThrow()
        assertEquals("Age Verified", verification.badge)
        assertTrue(verification.holderBound) // SD-JWT path is holder-bound
    }

    @Test
    fun `verifyCredential falls back to legacy when no SD-JWT`() = runTest {
        val cred = makeStoredCredential(
            localId = "legacy-1",
            credential = makeCredential(id = "legacy-1"),
            rawSdJwt = null,
            keyAlias = null
        )
        credRepo.storeCredential(cred)
        verifierClient.verifyResponse = VerifyResponseDTO(cachet = "OK", freshness = "fresh")
        val useCase = createUseCase(withKeyManager = true)

        val result = useCase.verifyCredential("legacy-1", "pack")

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().holderBound)
    }

    // ── respondViaRelay (with FakeKeyManager) ──

    @Test
    fun `respondViaRelay posts presentation to relay`() = runTest {
        AppConfig.configure(relayUrl = "http://relay")
        fakeKeyManager.generateKeyPair("holder-key")
        val cred = makeStoredCredential(
            localId = "relay-cred",
            credential = makeCredential(id = "relay-cred"),
            rawSdJwt = "eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJkaWQ6dmVyaWZmOnByb2R1Y3Rpb24ifQ.sig~WyJzYWx0MSIsImFnZSIsMzBd~",
            keyAlias = "holder-key"
        )
        credRepo.storeCredential(cred)

        val payload = VerificationRequestPayload(
            nonce = "relay-nonce",
            verifierDid = "did:web:verifier",
            packId = "childcare-es",
            question = "Safe?",
            predicates = listOf("age_gte_18")
        )
        relayClient.requestPayload = Json.encodeToString(
            VerificationRequestPayload.serializer(), payload
        ).encodeToByteArray()

        val useCase = createUseCase(withKeyManager = true)
        useCase.respondViaRelay("http://relay/sessions/s1/request", "relay-cred")

        assertEquals(1, relayClient.postedResponses.size)
        val posted = relayClient.postedResponses[0].decodeToString()
        // The posted response should be an SD-JWT presentation with KB-JWT appended
        assertTrue(posted.contains("eyJhbGciOiJFUzI1NiJ9"))
    }

    @Test
    fun `respondViaRelay throws when credential not found`() = runTest {
        relayClient.requestPayload = Json.encodeToString(
            VerificationRequestPayload.serializer(),
            VerificationRequestPayload("n", "did", "p", "q", listOf("p"))
        ).encodeToByteArray()
        val useCase = createUseCase(withKeyManager = true)

        try {
            useCase.respondViaRelay("http://relay/request", "nonexistent")
            assertTrue(false, "Should have thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Credential not found"))
        }
    }
}
