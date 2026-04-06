package id.cachet.wallet.domain.repository

import id.cachet.wallet.testfixtures.FakeCredentialRepository
import id.cachet.wallet.testfixtures.makeCredential
import id.cachet.wallet.testfixtures.makeStoredCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialRepositoryTest {

    // ── FakeCredentialRepository contract tests ──

    @Test
    fun `store then getAll returns it`() = runTest {
        val repo = FakeCredentialRepository()
        val cred = makeStoredCredential(localId = "test-1")
        repo.storeCredential(cred)

        val all = repo.getAllCredentials()
        assertEquals(1, all.size)
        assertEquals("test-1", all[0].localId)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        val repo = FakeCredentialRepository()
        assertNull(repo.getCredentialById("nonexistent"))
    }

    @Test
    fun `getById returns stored credential`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "find-me"))

        val found = repo.getCredentialById("find-me")
        assertNotNull(found)
        assertEquals("find-me", found.localId)
    }

    @Test
    fun `getByIssuer filters correctly`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "a", credential = makeCredential(issuer = "issuer-X")))
        repo.storeCredential(makeStoredCredential(localId = "b", credential = makeCredential(issuer = "issuer-Y")))
        repo.storeCredential(makeStoredCredential(localId = "c", credential = makeCredential(issuer = "issuer-X")))

        val filtered = repo.getCredentialsByIssuer("issuer-X")
        assertEquals(2, filtered.size)
    }

    @Test
    fun `markRevoked sets flag`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "revoke-test"))

        repo.markCredentialRevoked("revoke-test")

        assertTrue(repo.getCredentialById("revoke-test")!!.isRevoked)
    }

    @Test
    fun `delete removes credential`() = runTest {
        val repo = FakeCredentialRepository()
        repo.storeCredential(makeStoredCredential(localId = "delete-me"))
        repo.storeCredential(makeStoredCredential(localId = "keep-me"))

        repo.deleteCredential("delete-me")

        assertNull(repo.getCredentialById("delete-me"))
        assertNotNull(repo.getCredentialById("keep-me"))
    }
}
