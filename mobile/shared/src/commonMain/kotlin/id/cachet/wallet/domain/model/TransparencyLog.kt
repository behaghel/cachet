package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Phase 2B: Transparency Log Models
 * 
 * Based on Certificate Transparency (RFC 6962) for tamper-evident logging
 * of consent receipt hashes with public auditability.
 */

/**
 * Entry in the transparency log containing a consent receipt hash
 */
@Serializable
data class LogEntry(
    val index: Long,
    val timestamp: Instant,
    val receiptHash: String,
    val saltHash: String,
    val policyId: String? = null,
    val jurisdiction: String? = null
)

/**
 * Signed Tree Head (STH) - cryptographically commits to the entire log state
 */
@Serializable
data class SignedTreeHead(
    val treeSize: Long,
    val rootHash: String,
    val timestamp: Instant,
    val logId: String,
    val signature: String
)


/**
 * Consistency proof showing log append-only property between two STHs
 */
@Serializable
data class ConsistencyProof(
    val firstTreeSize: Long,
    val secondTreeSize: Long,
    val consistency: List<String> // Consistency proof hashes
)

/**
 * Request to add a consent receipt hash to the transparency log
 */
@Serializable
data class AddEntryRequest(
    val receiptHash: String,
    val saltHash: String,
    val policyId: String? = null,
    val jurisdiction: String? = null
)

/**
 * Response from adding an entry to the transparency log
 */
@Serializable
data class AddEntryResponse(
    val sct: SignedCertificateTimestamp
)


/**
 * Auditor report on transparency log health
 */
@Serializable
data class AuditReport(
    val logId: String,
    val reportTimestamp: Instant,
    val treeSize: Long,
    val rootHash: String,
    val consistencyVerified: Boolean,
    val inclusionChecksPerformed: Int,
    val inclusionFailures: Int,
    val auditorId: String
)

/**
 * Monitor configuration for transparency log oversight
 */
@Serializable
data class MonitorConfig(
    val logUrl: String,
    val auditInterval: Long = 3600, // seconds
    val inclusionCheckSamples: Int = 100,
    val consistencyCheckEnabled: Boolean = true,
    val auditorPublicKey: String
)