package id.cachet.wallet.domain.usecase

import id.cachet.wallet.network.OpenID4VCIException
import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.FakeOpenID4VCIClient
import id.cachet.wallet.testfixtures.makeCredential
import id.cachet.wallet.testfixtures.makeCredentialResponse
import id.cachet.wallet.testfixtures.makeStoredCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuanceUseCaseTest {

    private fun createUseCase(
        repo: FakeCredentialRepository = FakeCredentialRepository(),
        client: FakeOpenID4VCIClient = FakeOpenID4VCIClient()
    ) = Triple(repo, client, IssuanceUseCase(repo, client))

    // ── requestCredential ──

    @Test
    fun `requestCredential stores credential on success`() = runTest {
        val (repo, client, useCase) = createUseCase()
        client.credentialResponse = makeCredentialResponse()

        val result = useCase.requestCredential("client-1", listOf("IdentityCredential"))

        assertTrue(result.isSuccess)
        val stored = repo.getAllCredentials()
        assertEquals(1, stored.size)
        assertEquals("did:web:issuer.cachet.id", stored[0].credential.issuer)
    }

    @Test
    fun `requestCredential fails on token error`() = runTest {
        val (_, client, useCase) = createUseCase()
        client.tokenError = OpenID4VCIException("Token failed")

        val result = useCase.requestCredential("client-1", listOf("IdentityCredential"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IssuanceException)
    }

    @Test
    fun `requestCredential fails on credential error`() = runTest {
        val (_, client, useCase) = createUseCase()
        client.credentialError = OpenID4VCIException("Credential failed")

        val result = useCase.requestCredential("client-1", listOf("IdentityCredential"))

        assertTrue(result.isFailure)
    }

    // ── getStoredCredentials ──

    @Test
    fun `getStoredCredentials returns empty list initially`() = runTest {
        val (_, _, useCase) = createUseCase()
        val result = useCase.getStoredCredentials()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `getStoredCredentials returns stored credentials`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "id-1"))
        repo.storeCredential(makeStoredCredential(localId = "id-2"))
        val useCase = IssuanceUseCase(repo, FakeOpenID4VCIClient())

        val result = useCase.getStoredCredentials()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    // ── revokeCredential ──

    @Test
    fun `revokeCredential marks credential as revoked`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "revoke-me"))
        val useCase = IssuanceUseCase(repo, FakeOpenID4VCIClient())

        val result = useCase.revokeCredential("revoke-me")

        assertTrue(result.isSuccess)
        assertTrue(repo.getCredentialById("revoke-me")!!.isRevoked)
    }

    // ── getCredentialsByIssuer ──

    @Test
    fun `getCredentialsByIssuer filters correctly`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "a", credential = makeCredential(issuer = "issuer-A")))
        repo.storeCredential(makeStoredCredential(localId = "b", credential = makeCredential(issuer = "issuer-B")))
        repo.storeCredential(makeStoredCredential(localId = "c", credential = makeCredential(issuer = "issuer-A")))
        val useCase = IssuanceUseCase(repo, FakeOpenID4VCIClient())

        val result = useCase.getCredentialsByIssuer("issuer-A")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }
}
