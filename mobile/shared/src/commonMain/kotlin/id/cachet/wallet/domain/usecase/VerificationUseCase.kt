package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.crypto.KBJWTBuilder
import id.cachet.wallet.domain.crypto.KeyManager
import id.cachet.wallet.domain.crypto.SDJWTParser
import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.network.*

/**
 * Use case for verifying stored credentials against Trust Packs.
 * Supports both legacy JSON credentials and SD-JWT presentations with KB-JWT holder binding.
 * The relay client enables cross-device verification via the relay service.
 */
class VerificationUseCase(
    private val credentialRepository: CredentialRepository,
    private val verifierClient: VerifierClient,
    private val relayClient: RelayClient,
    private val consentUseCase: ConsentUseCase,
    private val keyManager: KeyManager? = null
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
     * If the credential has an SD-JWT and holder key, uses the secure path:
     *   1. Create session (get nonce + verifier DID)
     *   2. Parse SD-JWT and select disclosures for requested predicates
     *   3. Build KB-JWT with nonce, audience, sd_hash
     *   4. Send SD-JWT presentation to verifier
     * Otherwise falls back to legacy JSON path.
     */
    suspend fun verifyCredential(
        credentialId: String,
        packId: String
    ): Result<VerificationResult> {
        return try {
            val stored = credentialRepository.getCredentialById(credentialId)
                ?: return Result.failure(Exception("Credential not found: $credentialId"))

            val response: VerifyResponseDTO
            val isSDJWT: Boolean

            // SD-JWT path: cryptographically bound presentation
            if (stored.rawSdJwt != null && stored.keyAlias != null && keyManager != null) {
                response = verifyWithSDJWT(stored, packId)
                isSDJWT = true
            } else {
                // Legacy path: plain JSON credential
                response = verifyWithLegacy(stored, packId)
                isSDJWT = false
            }

            // Generate consent receipt for the satisfied predicates
            val receipt = generateReceiptForVerification(stored.credential, packId, response)

            val result = VerificationResult(
                packId = packId,
                badge = response.badge,
                freshness = response.freshness,
                predicateResults = response.predicateResults,
                summary = response.summary,
                consentReceiptId = receipt?.id,
                holderBound = isSDJWT
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Secure SD-JWT presentation with KB-JWT holder binding.
     */
    private suspend fun verifyWithSDJWT(
        stored: StoredCredential,
        packId: String
    ): VerifyResponseDTO {
        val km = keyManager!!
        val rawSdJwt = stored.rawSdJwt!!
        val keyAlias = stored.keyAlias!!

        // Step 1: Create verification session to get nonce + verifier DID
        val session = verifierClient.createSession()

        // Step 2: Parse SD-JWT and prepare selective disclosure
        val parsed = SDJWTParser.parse(rawSdJwt)
        // For now, include all disclosures. Future: match against pack's requested predicates.
        val presentation = SDJWTParser.selectivePresentation(parsed, parsed.claims.keys)

        // Step 3: Build KB-JWT with session nonce + verifier audience
        val kbjwt = KBJWTBuilder.build(
            nonce = session.nonce,
            audience = session.verifierDid,
            sdJwtWithDisclosures = presentation,
            keyManager = km,
            keyAlias = keyAlias
        )

        // Step 4: Append KB-JWT to the presentation
        val fullPresentation = presentation + kbjwt

        // Step 5: Send to verifier with session ID for nonce validation
        return verifierClient.verifySDJWTPresentation(
            policyId = packId,
            sdJwtCredentials = listOf(fullPresentation),
            sessionId = session.sessionId
        )
    }

    /**
     * Legacy JSON credential presentation (no cryptographic binding).
     */
    private suspend fun verifyWithLegacy(
        stored: StoredCredential,
        packId: String
    ): VerifyResponseDTO {
        val credDTO = toCredentialDTO(stored.credential)
        return verifierClient.verifyPresentation(packId, listOf(credDTO))
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
    val consentReceiptId: String?,
    val holderBound: Boolean = false
)
