package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.StoredCredential

interface CredentialRepository {
    suspend fun storeCredential(credential: StoredCredential)
    suspend fun getAllCredentials(): List<StoredCredential>
    suspend fun getCredentialById(localId: String): StoredCredential?
    suspend fun getCredentialsByIssuer(issuer: String): List<StoredCredential>
    suspend fun markCredentialRevoked(localId: String)
    suspend fun deleteCredential(localId: String)
}