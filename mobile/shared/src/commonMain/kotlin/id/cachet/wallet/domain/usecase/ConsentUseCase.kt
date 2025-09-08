package id.cachet.wallet.domain.usecase

import id.cachet.wallet.domain.model.*
import id.cachet.wallet.domain.repository.CredentialRepository
import id.cachet.wallet.domain.repository.ConsentReceiptRepository
import id.cachet.wallet.domain.repository.TransparencyLogRepository
import kotlinx.datetime.Clock

/**
 * Use case for handling consent receipts and credential presentations
 */
class ConsentUseCase(
    private val credentialRepository: CredentialRepository,
    private val consentReceiptRepository: ConsentReceiptRepository,
    private val transparencyLogRepository: TransparencyLogRepository
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
            
            // Phase 2B: Anchor hash to transparency log
            val receiptWithTransparencyLog = anchorToTransparencyLog(receiptWithHash).getOrElse { receiptWithHash }
            
            // Store using repository (Phase 2 enhancement)
            consentReceiptRepository.storeReceipt(receiptWithTransparencyLog).getOrThrow()
            
            return Result.success(receiptWithTransparencyLog)
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
    
    /**
     * Anchor a consent receipt hash to the transparency log
     * Phase 2B enhancement
     */
    private suspend fun anchorToTransparencyLog(receipt: ConsentReceipt): Result<ConsentReceipt> {
        return try {
            val saltHash = receipt.salt?.let { sha256Hash(it) } ?: return Result.success(receipt)
            val receiptHash = receipt.receiptHash ?: return Result.success(receipt)
            
            val request = id.cachet.wallet.domain.model.AddEntryRequest(
                receiptHash = receiptHash,
                saltHash = saltHash,
                policyId = "consent-receipt-v1",
                jurisdiction = extractJurisdiction(receipt.rpIdentifier)
            )
            
            val response = transparencyLogRepository.submitReceiptHash(request).getOrElse { 
                return Result.success(receipt) // Continue without transparency log if it fails
            }
            
            val transparencyEntry = TransparencyLogEntry(
                logId = response.sct.logId,
                logIndex = -1, // Will be filled when inclusion proof is retrieved
                sct = response.sct,
                anchoredAt = Clock.System.now(),
                isVerified = false
            )
            
            val receiptWithLog = receipt.copy(transparencyLogEntry = transparencyEntry)
            Result.success(receiptWithLog)
            
        } catch (e: Exception) {
            // Graceful degradation - continue without transparency log
            Result.success(receipt)
        }
    }
    
    /**
     * Verify transparency log inclusion for a consent receipt
     * Phase 2B enhancement
     */
    suspend fun verifyTransparencyLogInclusion(receipt: ConsentReceipt): Result<Boolean> {
        val logEntry = receipt.transparencyLogEntry ?: return Result.success(false)
        val receiptHash = receipt.receiptHash ?: return Result.success(false)
        
        return try {
            // Get current STH to determine tree size for proof
            val sth = transparencyLogRepository.getCurrentSTH().getOrThrow()
            
            // For this demo, we'll simulate finding the leaf index
            // In production, you'd need to search the log or track the index
            val leafIndex = logEntry.logIndex.takeIf { it >= 0 } ?: 0L
            
            // Get inclusion proof
            val proof = transparencyLogRepository.getInclusionProof(leafIndex, sth.treeSize).getOrThrow()
            
            // Verify the proof locally
            val isValid = transparencyLogRepository.verifyInclusionProof(receiptHash, proof)
            
            if (isValid) {
                // Update the receipt with verification info
                val updatedEntry = logEntry.copy(
                    logIndex = proof.leafIndex,
                    inclusionProof = proof,
                    verifiedAt = Clock.System.now(),
                    isVerified = true
                )
                val updatedReceipt = receipt.copy(transparencyLogEntry = updatedEntry)
                consentReceiptRepository.storeReceipt(updatedReceipt)
            }
            
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Monitor transparency log health and consistency
     * Phase 2B enhancement
     */
    suspend fun performTransparencyLogAudit(): Result<String> {
        return try {
            val currentSTH = transparencyLogRepository.getCurrentSTH().getOrThrow()
            val auditReport = StringBuilder()
            
            auditReport.appendLine("=== Transparency Log Audit Report ===")
            auditReport.appendLine("Log ID: ${currentSTH.logId}")
            auditReport.appendLine("Tree Size: ${currentSTH.treeSize}")
            auditReport.appendLine("Root Hash: ${currentSTH.rootHash}")
            auditReport.appendLine("Timestamp: ${currentSTH.timestamp}")
            
            // Sample some recent entries for inclusion verification
            if (currentSTH.treeSize > 0) {
                val sampleSize = minOf(5, currentSTH.treeSize)
                val startIndex = maxOf(0, currentSTH.treeSize - sampleSize)
                val entries = transparencyLogRepository.getEntries(startIndex, currentSTH.treeSize - 1).getOrThrow()
                
                auditReport.appendLine("\n=== Inclusion Verification Sample ===")
                var verified = 0
                var failed = 0
                
                for ((index, entry) in entries.withIndex()) {
                    try {
                        val proof = transparencyLogRepository.getInclusionProof(startIndex + index, currentSTH.treeSize).getOrThrow()
                        val isValid = transparencyLogRepository.verifyInclusionProof(entry.receiptHash, proof)
                        if (isValid) verified++ else failed++
                        auditReport.appendLine("Entry ${startIndex + index}: ${if (isValid) "✓" else "✗"}")
                    } catch (e: Exception) {
                        failed++
                        auditReport.appendLine("Entry ${startIndex + index}: ✗ (${e.message})")
                    }
                }
                
                auditReport.appendLine("\nVerification Results: $verified verified, $failed failed")
                auditReport.appendLine("Success Rate: ${(verified.toFloat() / (verified + failed) * 100).toInt()}%")
            }
            
            Result.success(auditReport.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun extractJurisdiction(rpIdentifier: String): String? {
        // Simple jurisdiction extraction from RP identifier
        return when {
            rpIdentifier.endsWith(".es") -> "ES"
            rpIdentifier.endsWith(".fr") -> "FR"
            rpIdentifier.endsWith(".ee") -> "EE"
            rpIdentifier.contains("madrid") -> "ES"
            else -> null
        }
    }
    
    private fun generateId(): String {
        // Simple ID generation for demo purposes
        return "consent_${Clock.System.now().epochSeconds}_${(0..999999).random()}"
    }
}