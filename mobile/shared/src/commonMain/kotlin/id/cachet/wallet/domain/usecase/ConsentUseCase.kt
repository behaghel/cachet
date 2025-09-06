package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import kotlinx.datetime.Clock

/**
 * Use case for handling consent receipts and credential presentations
 */
class ConsentUseCase(
    private val credentialRepository: CredentialRepository
) {
    
    /**
     * Generate a consent receipt for a credential presentation
     */
    suspend fun generateConsentReceipt(
        credential: VerifiableCredential,
        presentationRequest: PresentationRequest,
        userConsent: ConsentDetails
    ): Result<ConsentReceipt> {
        try {
            val receipt = ConsentReceipt(
                id = generateId(),
                timestamp = Clock.System.now(),
                purpose = presentationRequest.purpose,
                predicatesProven = presentationRequest.requestedPredicates,
                rpIdentifier = presentationRequest.rpIdentifier,
                rpDisplayName = presentationRequest.rpDisplayName,
                userConsent = userConsent,
                credentialId = credential.id
            )
            
            // Generate hash for transparency logging
            val hashValue = receipt.generateHash()
            val receiptWithHash = receipt.copy(receiptHash = hashValue)
            
            // Store locally (in production, also anchor hash to transparency log)
            storeConsentReceipt(receiptWithHash)
            
            return Result.success(receiptWithHash)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Present a credential with explicit consent
     */
    suspend fun presentCredential(
        credentialId: String,
        presentationRequest: PresentationRequest,
        userConsent: ConsentDetails
    ): Result<PresentationResult> {
        try {
            // Get the credential
            val storedCredential = credentialRepository.getCredentialById(credentialId)
                ?: return Result.success(
                    PresentationResult(
                        success = false,
                        predicatesProven = emptyList(),
                        consentReceipt = null,
                        errorMessage = "Credential not found"
                    )
                )
            
            val credential = storedCredential.credential
            
            // Check if credential can satisfy the request
            if (!credential.canSatisfyRequest(presentationRequest)) {
                return Result.success(
                    PresentationResult(
                        success = false,
                        predicatesProven = emptyList(),
                        consentReceipt = null,
                        errorMessage = "Credential cannot satisfy requested predicates"
                    )
                )
            }
            
            // Check if credential is still valid
            if (credential.isExpired() || storedCredential.isRevoked) {
                return Result.success(
                    PresentationResult(
                        success = false,
                        predicatesProven = emptyList(),
                        consentReceipt = null,
                        errorMessage = "Credential is expired or revoked"
                    )
                )
            }
            
            // Generate consent receipt
            val receiptResult = generateConsentReceipt(
                credential = credential,
                presentationRequest = presentationRequest,
                userConsent = userConsent
            )
            
            return receiptResult.fold(
                onSuccess = { receipt ->
                    Result.success(
                        PresentationResult(
                            success = true,
                            predicatesProven = presentationRequest.requestedPredicates,
                            consentReceipt = receipt
                        )
                    )
                },
                onFailure = { error ->
                    Result.success(
                        PresentationResult(
                            success = false,
                            predicatesProven = emptyList(),
                            consentReceipt = null,
                            errorMessage = "Failed to generate consent receipt: ${error.message}"
                        )
                    )
                }
            )
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Get all consent receipts for audit/transparency
     */
    suspend fun getConsentReceipts(): Result<List<ConsentReceipt>> {
        return try {
            // In production, this would query a local database
            val receipts = getStoredConsentReceipts()
            Result.success(receipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validate a presentation request before showing consent UI
     */
    fun validatePresentationRequest(request: PresentationRequest): Boolean {
        return request.rpIdentifier.isNotBlank() && 
               request.purpose.isNotBlank() && 
               request.purpose.length >= 10 &&
               request.requestedPredicates.isNotEmpty()
    }
    
    // In-memory storage for demo (production would use proper persistence)
    private val consentReceiptStore = mutableListOf<ConsentReceipt>()
    
    private fun storeConsentReceipt(receipt: ConsentReceipt) {
        consentReceiptStore.add(receipt)
    }
    
    private fun getStoredConsentReceipts(): List<ConsentReceipt> {
        return consentReceiptStore.toList()
    }
    
    private fun generateId(): String {
        // Simple ID generation for demo purposes
        return "consent_${Clock.System.now().epochSeconds}_${(0..999999).random()}"
    }
}