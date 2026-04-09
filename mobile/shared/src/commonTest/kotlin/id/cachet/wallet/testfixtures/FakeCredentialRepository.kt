package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.model.StoredCredential
import id.cachet.wallet.domain.repository.CredentialRepository

class FakeCredentialRepository : CredentialRepository {
    private val credentials = mutableListOf<StoredCredential>()

    override suspend fun storeCredential(credential: StoredCredential) {
        credentials.add(credential)
    }

    override suspend fun getAllCredentials(): List<StoredCredential> = credentials.toList()

    override suspend fun getCredentialById(localId: String): StoredCredential? =
        credentials.find { it.localId == localId }

    override suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential> =
        credentials.filter { it.credential.issuer == issuer }

    override suspend fun markCredentialRevoked(localId: String) {
        val index = credentials.indexOfFirst { it.localId == localId }
        if (index >= 0) {
            credentials[index] = credentials[index].copy(isRevoked = true)
        }
    }

    override suspend fun deleteCredential(localId: String) {
        credentials.removeAll { it.localId == localId }
    }
}
