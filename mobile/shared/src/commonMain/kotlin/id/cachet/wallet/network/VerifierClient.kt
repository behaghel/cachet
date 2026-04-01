package id.cachet.wallet.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

interface VerifierClient {
    suspend fun listPacks(): List<PackSummary>
    suspend fun verifyPresentation(policyId: String, credentials: List<VerifiableCredentialDTO>): VerifyResponseDTO
}

@Serializable
data class PackSummary(
    val id: String,
    val version: String,
    val name: String
)

@Serializable
data class VerifyRequestDTO(
    val policyId: String,
    val bundle: BundleDTO
)

@Serializable
data class BundleDTO(
    val credentials: List<VerifiableCredentialDTO>
)

@Serializable
data class VerifiableCredentialDTO(
    val id: String,
    val type: List<String> = emptyList(),
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialSubject: CredentialSubjectDTO
)

@Serializable
data class CredentialSubjectDTO(
    val id: String,
    val verified: Boolean? = null,
    val personalData: PersonalDataDTO? = null,
    val verificationLevel: String? = null,
    val verificationMethod: String? = null
)

@Serializable
data class PersonalDataDTO(
    val age: Int? = null,
    val nationality: String? = null,
    val documentType: String? = null
)

@Serializable
data class VerifyResponseDTO(
    val badge: String,
    val predicates: List<String> = emptyList(),
    val freshness: String,
    val predicateResults: List<PredicateResultDTO> = emptyList(),
    val summary: VerificationSummaryDTO? = null
)

@Serializable
data class PredicateResultDTO(
    val predicateId: String,
    val status: String,
    val reason: String? = null
)

@Serializable
data class VerificationSummaryDTO(
    val requiredSatisfied: Int = 0,
    val requiredTotal: Int = 0,
    val optionalSatisfied: Int? = null,
    val optionalTotal: Int? = null,
    val badgeGranted: Boolean = false
)

class VerifierException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KtorVerifierClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : VerifierClient {

    override suspend fun listPacks(): List<PackSummary> {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/packs")
            if (response.status.isSuccess()) {
                return response.body<List<PackSummary>>()
            }
            throw VerifierException("Failed to list packs: ${response.status}")
        } catch (e: Exception) {
            if (e is VerifierException) throw e
            throw VerifierException("Network error listing packs", e)
        }
    }

    override suspend fun verifyPresentation(
        policyId: String,
        credentials: List<VerifiableCredentialDTO>
    ): VerifyResponseDTO {
        try {
            val request = VerifyRequestDTO(
                policyId = policyId,
                bundle = BundleDTO(credentials = credentials)
            )
            val response: HttpResponse = httpClient.post("$baseUrl/presentations/verify") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                return response.body<VerifyResponseDTO>()
            }
            throw VerifierException("Verification failed: ${response.status}")
        } catch (e: Exception) {
            if (e is VerifierException) throw e
            throw VerifierException("Network error during verification", e)
        }
    }
}
