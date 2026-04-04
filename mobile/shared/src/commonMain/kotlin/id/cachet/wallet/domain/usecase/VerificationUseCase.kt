package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.*

/**
 * Use case for verifying stored credentials against Trust Packs.
 */
class VerificationUseCase(
    private val credentialRepository: CredentialRepository,
    private val verifierClient: VerifierClient,
    private val consentUseCase: ConsentUseCase
) {

    /**
     * Fetch available Trust Packs from the verifier.
     */
    suspend fun getAvailablePacks(): Result<List<PackSummary>> {
        return try {
            Result.success(verifierClient.listPacks())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify a stored credential against a Trust Pack.
     * Returns the verification response including per-predicate results.
     */
    suspend fun verifyCredential(
        credentialId: String,
        packId: String
    ): Result<VerificationResult> {
        return try {
            // Fetch the credential
            val stored = credentialRepository.getCredentialById(credentialId)
                ?: return Result.failure(Exception("Credential not found: $credentialId"))

            // Convert to the DTO format the verifier expects
            val credDTO = toCredentialDTO(stored.credential)

            // Call the verifier
            val response = verifierClient.verifyPresentation(packId, listOf(credDTO))

            // Generate consent receipt for the satisfied predicates
            val receipt = generateReceiptForVerification(stored.credential, packId, response)

            val result = VerificationResult(
                packId = packId,
                badge = response.badge,
                freshness = response.freshness,
                predicateResults = response.predicateResults,
                summary = response.summary,
                consentReceiptId = receipt?.id
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun generateReceiptForVerification(
        credential: VerifiableCredential,
        packId: String,
        response: VerifyResponseDTO
    ): ConsentReceipt? {
        if (response.predicates.isEmpty()) return null

        val request = PresentationRequest(
            rpIdentifier = "did:web:cachet.id:verifier",
            rpDisplayName = "Cachet Verifier",
            purpose = "Trust Pack verification: $packId",
            requestedPredicates = response.predicates
        )
        val consent = ConsentDetails(
            explicitConsent = true,
            dataMinimizationAcknowledged = true,
            retentionPeriodUnderstood = true
        )

        return consentUseCase.generateConsentReceipt(credential, request, consent).getOrNull()
    }

    private fun toCredentialDTO(vc: VerifiableCredential): VerifiableCredentialDTO {
        return VerifiableCredentialDTO(
            id = vc.id,
            type = vc.type,
            issuer = vc.issuer,
            issuanceDate = vc.issuanceDate,
            expirationDate = vc.expirationDate,
            credentialSubject = CredentialSubjectDTO(
                id = vc.credentialSubject.id,
                verified = vc.credentialSubject.verified,
                personalData = vc.credentialSubject.personalData?.let {
                    PersonalDataDTO(
                        age = it.age,
                        nationality = it.nationality,
                        documentType = it.documentType
                    )
                },
                verificationLevel = vc.credentialSubject.verificationLevel,
                verificationMethod = vc.credentialSubject.verificationMethod
            )
        )
    }
}

/**
 * Result of verifying a credential against a Trust Pack.
 */
data class VerificationResult(
    val packId: String,
    val badge: String,
    val freshness: String,
    val predicateResults: List<PredicateResultDTO>,
    val summary: VerificationSummaryDTO?,
    val consentReceiptId: String?
)
