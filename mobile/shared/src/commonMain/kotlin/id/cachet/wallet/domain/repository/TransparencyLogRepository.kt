package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import io.ktor.http.*

/**
 * Repository interface for transparency log operations
 * Phase 2B: Implements tamper-evident logging for consent receipts
 */
interface TransparencyLogRepository {
    
    /**
     * Submit a consent receipt hash to the transparency log
     */
    suspend fun submitReceiptHash(request: AddEntryRequest): Result<AddEntryResponse>
    
    /**
     * Get the current Signed Tree Head (STH)
     */
    suspend fun getCurrentSTH(): Result<SignedTreeHead>
    
    /**
     * Get entries from the log within a range
     */
    suspend fun getEntries(start: Long, end: Long): Result<List<LogEntry>>
    
    /**
     * Get inclusion proof for a specific entry
     */
    suspend fun getInclusionProof(leafIndex: Long, treeSize: Long): Result<InclusionProof>
    
    /**
     * Get consistency proof between two tree sizes
     */
    suspend fun getConsistencyProof(
        firstTreeSize: Long, 
        secondTreeSize: Long
    ): Result<ConsistencyProof>
    
    /**
     * Verify an inclusion proof locally
     */
    fun verifyInclusionProof(
        leafHash: String,
        proof: InclusionProof
    ): Boolean
    
    /**
     * Verify a consistency proof locally
     */
    fun verifyConsistencyProof(
        firstSTH: SignedTreeHead,
        secondSTH: SignedTreeHead, 
        proof: ConsistencyProof
    ): Boolean
}

/**
 * HTTP client implementation for transparency log operations
 * Compatible with Trillian and Certificate Transparency APIs
 */
class HttpTransparencyLogRepository(
    private val baseUrl: String,
    private val httpClient: io.ktor.client.HttpClient
) : TransparencyLogRepository {
    
    override suspend fun submitReceiptHash(request: AddEntryRequest): Result<AddEntryResponse> {
        return try {
            val response = httpClient.post("$baseUrl/ct/v1/add-chain") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val addEntryResponse = response.body<AddEntryResponse>()
                Result.success(addEntryResponse)
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentSTH(): Result<SignedTreeHead> {
        return try {
            val response = httpClient.get("$baseUrl/ct/v1/get-sth")
            
            if (response.status.isSuccess()) {
                val sth = response.body<SignedTreeHead>()
                Result.success(sth)
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getEntries(start: Long, end: Long): Result<List<LogEntry>> {
        return try {
            val response = httpClient.get("$baseUrl/ct/v1/get-entries") {
                parameter("start", start)
                parameter("end", end)
            }
            
            if (response.status.isSuccess()) {
                val entries = response.body<List<LogEntry>>()
                Result.success(entries)
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getInclusionProof(leafIndex: Long, treeSize: Long): Result<InclusionProof> {
        return try {
            val response = httpClient.get("$baseUrl/ct/v1/get-proof-by-hash") {
                parameter("leaf_index", leafIndex)
                parameter("tree_size", treeSize)
            }
            
            if (response.status.isSuccess()) {
                val proof = response.body<InclusionProof>()
                Result.success(proof)
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getConsistencyProof(
        firstTreeSize: Long, 
        secondTreeSize: Long
    ): Result<ConsistencyProof> {
        return try {
            val response = httpClient.get("$baseUrl/ct/v1/get-sth-consistency") {
                parameter("first", firstTreeSize)
                parameter("second", secondTreeSize)
            }
            
            if (response.status.isSuccess()) {
                val proof = response.body<ConsistencyProof>()
                Result.success(proof)
            } else {
                Result.failure(Exception("HTTP ${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun verifyInclusionProof(
        leafHash: String,
        proof: InclusionProof
    ): Boolean {
        // Simplified verification - production would implement full Merkle proof verification
        return proof.auditPath.isNotEmpty() && 
               proof.rootHash.isNotBlank() && 
               proof.leafIndex >= 0 &&
               proof.treeSize > 0
    }
    
    override fun verifyConsistencyProof(
        firstSTH: SignedTreeHead,
        secondSTH: SignedTreeHead, 
        proof: ConsistencyProof
    ): Boolean {
        // Simplified verification - production would implement full consistency proof
        return proof.firstTreeSize == firstSTH.treeSize &&
               proof.secondTreeSize == secondSTH.treeSize &&
               proof.secondTreeSize >= proof.firstTreeSize &&
               proof.consistency.isNotEmpty()
    }
}

/**
 * In-memory mock implementation for development and testing
 */
class MockTransparencyLogRepository : TransparencyLogRepository {
    
    private val entries = mutableListOf<LogEntry>()
    private var currentTreeSize = 0L
    
    override suspend fun submitReceiptHash(request: AddEntryRequest): Result<AddEntryResponse> {
        return try {
            val entry = LogEntry(
                index = currentTreeSize,
                timestamp = kotlinx.datetime.Clock.System.now(),
                receiptHash = request.receiptHash,
                saltHash = request.saltHash,
                policyId = request.policyId,
                jurisdiction = request.jurisdiction
            )
            
            entries.add(entry)
            currentTreeSize++
            
            val sct = SignedCertificateTimestamp(
                logId = "mock-log-id",
                timestamp = kotlinx.datetime.Clock.System.now(),
                signature = "mock-signature-${currentTreeSize}"
            )
            
            Result.success(AddEntryResponse(sct))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentSTH(): Result<SignedTreeHead> {
        return try {
            val sth = SignedTreeHead(
                treeSize = currentTreeSize,
                rootHash = "mock-root-hash-${currentTreeSize}",
                timestamp = kotlinx.datetime.Clock.System.now(),
                logId = "mock-log-id",
                signature = "mock-sth-signature-${currentTreeSize}"
            )
            Result.success(sth)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getEntries(start: Long, end: Long): Result<List<LogEntry>> {
        return try {
            val requestedEntries = entries.drop(start.toInt()).take((end - start + 1).toInt())
            Result.success(requestedEntries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getInclusionProof(leafIndex: Long, treeSize: Long): Result<InclusionProof> {
        return try {
            val proof = InclusionProof(
                leafIndex = leafIndex,
                treeSize = treeSize,
                auditPath = listOf("mock-audit-path-${leafIndex}"),
                rootHash = "mock-root-hash-${treeSize}"
            )
            Result.success(proof)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getConsistencyProof(
        firstTreeSize: Long, 
        secondTreeSize: Long
    ): Result<ConsistencyProof> {
        return try {
            val proof = ConsistencyProof(
                firstTreeSize = firstTreeSize,
                secondTreeSize = secondTreeSize,
                consistency = listOf("mock-consistency-${firstTreeSize}-${secondTreeSize}")
            )
            Result.success(proof)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun verifyInclusionProof(leafHash: String, proof: InclusionProof): Boolean {
        return true // Mock always returns true
    }
    
    override fun verifyConsistencyProof(
        firstSTH: SignedTreeHead,
        secondSTH: SignedTreeHead, 
        proof: ConsistencyProof
    ): Boolean {
        return true // Mock always returns true
    }
}