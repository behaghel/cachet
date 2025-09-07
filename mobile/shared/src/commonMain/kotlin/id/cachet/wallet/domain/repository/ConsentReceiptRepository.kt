package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.ConsentReceipt

/**
 * Repository interface for consent receipt storage and retrieval
 * Phase 2 enhancement for proper persistence
 */
interface ConsentReceiptRepository {
    
    /**
     * Store a consent receipt securely
     */
    suspend fun storeReceipt(receipt: ConsentReceipt): Result<Unit>
    
    /**
     * Retrieve a consent receipt by ID
     */
    suspend fun getReceiptById(receiptId: String): Result<ConsentReceipt?>
    
    /**
     * Get all consent receipts for audit purposes
     */
    suspend fun getAllReceipts(): Result<List<ConsentReceipt>>
    
    /**
     * Get consent receipts for a specific relying party
     */
    suspend fun getReceiptsByRP(rpIdentifier: String): Result<List<ConsentReceipt>>
    
    /**
     * Get consent receipts for a specific credential
     */
    suspend fun getReceiptsByCredential(credentialId: String): Result<List<ConsentReceipt>>
    
    /**
     * Delete a consent receipt (for revocation scenarios)
     */
    suspend fun deleteReceipt(receiptId: String): Result<Unit>
    
    /**
     * Verify the integrity of all stored receipts
     */
    suspend fun verifyAllReceipts(): Result<Map<String, Boolean>>
}

/**
 * In-memory implementation of ConsentReceiptRepository for demo purposes
 * Production would use SQLDelight database or encrypted local storage
 */
class InMemoryConsentReceiptRepository : ConsentReceiptRepository {
    
    private val receipts = mutableMapOf<String, ConsentReceipt>()
    
    override suspend fun storeReceipt(receipt: ConsentReceipt): Result<Unit> {
        return try {
            receipts[receipt.id] = receipt
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getReceiptById(receiptId: String): Result<ConsentReceipt?> {
        return try {
            val receipt = receipts[receiptId]
            Result.success(receipt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAllReceipts(): Result<List<ConsentReceipt>> {
        return try {
            val receiptList = receipts.values.toList()
            Result.success(receiptList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getReceiptsByRP(rpIdentifier: String): Result<List<ConsentReceipt>> {
        return try {
            val filteredReceipts = receipts.values.filter { it.rpIdentifier == rpIdentifier }
            Result.success(filteredReceipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getReceiptsByCredential(credentialId: String): Result<List<ConsentReceipt>> {
        return try {
            val filteredReceipts = receipts.values.filter { it.credentialId == credentialId }
            Result.success(filteredReceipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteReceipt(receiptId: String): Result<Unit> {
        return try {
            receipts.remove(receiptId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun verifyAllReceipts(): Result<Map<String, Boolean>> {
        return try {
            val verificationResults = receipts.mapValues { (_, receipt) ->
                // Simple verification - in production would use proper crypto verification
                receipt.receiptHash != null && receipt.signature != null && receipt.salt != null
            }
            Result.success(verificationResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}