package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import kotlinx.datetime.Clock

/**
 * Use case for handling consent receipts and credential presentations
 */
class ConsentUseCase(
    private val credentialRepository: CredentialRepository,
    private val consentReceiptRepository: ConsentReceiptRepository
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
            
            // Generate cryptographically secure hash and signature
            val salt = generateSalt()
            val hashValue = receipt.generateHash(salt)
            val canonicalContent = receipt.buildCanonicalRepresentation()
            val signature = signConsentReceipt(canonicalContent, getSigningKey())
            
            val receiptWithHash = receipt.copy(
                receiptHash = hashValue,
                signature = signature,
                salt = salt
            )
            
            // Store using repository (Phase 2 enhancement)
            consentReceiptRepository.storeReceipt(receiptWithHash).getOrThrow()
            
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
     * Get all consent receipts for audit/transparency (Phase 2 enhancement)
     */
    suspend fun getConsentReceipts(): Result<List<ConsentReceipt>> {
        return consentReceiptRepository.getAllReceipts()
    }
    
    /**
     * Get consent receipts for a specific relying party
     */
    suspend fun getConsentReceiptsByRP(rpIdentifier: String): Result<List<ConsentReceipt>> {
        return consentReceiptRepository.getReceiptsByRP(rpIdentifier)
    }
    
    /**
     * Get consent receipts for a specific credential
     */
    suspend fun getConsentReceiptsByCredential(credentialId: String): Result<List<ConsentReceipt>> {
        return consentReceiptRepository.getReceiptsByCredential(credentialId)
    }
    
    /**
     * Verify the integrity of all stored consent receipts
     */
    suspend fun verifyAllConsentReceipts(): Result<Map<String, Boolean>> {
        return try {
            val allReceipts = consentReceiptRepository.getAllReceipts().getOrThrow()
            val verificationResults = mutableMapOf<String, Boolean>()
            
            for (receipt in allReceipts) {
                val isValid = verifyConsentReceipt(receipt).getOrElse { false }
                verificationResults[receipt.id] = isValid
            }
            
            Result.success(verificationResults)
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
    
    /**
     * Verify the integrity of a consent receipt
     */
    suspend fun verifyConsentReceipt(receipt: ConsentReceipt): Result<Boolean> {
        try {
            // Verify hash integrity using stored salt
            val storedSalt = receipt.salt ?: return Result.success(false) // No salt means invalid
            val expectedHash = receipt.generateHash(storedSalt)
            val hashValid = receipt.receiptHash == expectedHash
            
            // Verify signature if present
            val signatureValid = receipt.signature?.let { signature ->
                val canonicalContent = receipt.buildCanonicalRepresentation()
                verifyConsentReceiptSignature(canonicalContent, signature, getVerificationKey())
            } ?: true // If no signature, consider valid for backward compatibility
            
            return Result.success(hashValid && signatureValid)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Get or generate a signing key for consent receipts
     * In production, this would use hardware-backed key storage
     */
    private fun getSigningKey(): String {
        // Placeholder implementation - would use proper key management
        return "demo_signing_key_${Clock.System.now().epochSeconds}"
    }
    
    /**
     * Get the corresponding verification key
     * In production, this would retrieve the public key
     */
    private fun getVerificationKey(): String {
        // Placeholder implementation - would use proper public key
        return "demo_verification_key"
    }
    
    private fun generateId(): String {
        // Simple ID generation for demo purposes
        return "consent_${Clock.System.now().epochSeconds}_${(0..999999).random()}"
    }
}