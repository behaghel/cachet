package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredential(
    val id: String,
    @SerialName("@context")
    val context: List<String>,
    val type: List<String>,
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialSubject: CredentialSubject,
    val credentialStatus: CredentialStatus? = null
) {
    fun isExpired(): Boolean {
        val expiryDateString = expirationDate ?: return false
        val expiryDate = try {
            Instant.parse(expiryDateString)
        } catch (e: Exception) {
            return false
        }
        return kotlinx.datetime.Clock.System.now() > expiryDate
    }

    fun getIssuanceInstant(): Instant? {
        return try {
            Instant.parse(issuanceDate)
        } catch (e: Exception) {
            null
        }
    }

    fun getSubjectId(): String = credentialSubject.id
}

@Serializable
data class CredentialStatus(
    val id: String,
    val type: String
)

@Serializable
data class StoredCredential(
    val localId: String,
    val credential: VerifiableCredential,
    val rawJwt: String? = null,
    val createdAt: Instant,
    val isRevoked: Boolean = false
)
