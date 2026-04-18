package id.cachet.wallet.domain.repository

import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.domain.model.ConsentDetails
import id.cachet.wallet.domain.model.ConsentReceipt
import id.cachet.wallet.domain.model.TransparencyLogEntry
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed implementation of ConsentReceiptRepository.
 * Persists consent receipts to the local database.
 */
class SqlDelightConsentReceiptRepository(
    private val database: WalletDatabase
) : ConsentReceiptRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun storeReceipt(receipt: ConsentReceipt): Result<Unit> {
        return try {
            database.walletDatabaseQueries.insertReceipt(
                id = receipt.id,
                timestamp = receipt.timestamp.toEpochMilliseconds(),
                purpose = receipt.purpose,
                predicates_json = json.encodeToString(receipt.predicatesProven),
                rp_identifier = receipt.rpIdentifier,
                rp_display_name = receipt.rpDisplayName,
                consent_json = json.encodeToString(receipt.userConsent),
                credential_id = receipt.credentialId,
                outcome = receipt.outcome,
                total_predicates_count = receipt.totalPredicatesCount.toLong(),
                receipt_hash = receipt.receiptHash,
                signature = receipt.signature,
                salt = receipt.salt,
                transparency_log_json = receipt.transparencyLogEntry?.let { json.encodeToString(it) }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReceiptById(receiptId: String): Result<ConsentReceipt?> {
        return try {
            val row = database.walletDatabaseQueries.getReceiptById(receiptId).executeAsOneOrNull()
            Result.success(row?.toConsentReceipt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllReceipts(): Result<List<ConsentReceipt>> {
        return try {
            val rows = database.walletDatabaseQueries.getAllReceipts().executeAsList()
            Result.success(rows.map { it.toConsentReceipt() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReceiptsByRP(rpIdentifier: String): Result<List<ConsentReceipt>> {
        return try {
            val rows = database.walletDatabaseQueries.getReceiptsByRP(rpIdentifier).executeAsList()
            Result.success(rows.map { it.toConsentReceipt() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReceiptsByCredential(credentialId: String): Result<List<ConsentReceipt>> {
        return try {
            val rows = database.walletDatabaseQueries.getReceiptsByCredential(credentialId).executeAsList()
            Result.success(rows.map { it.toConsentReceipt() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteReceipt(receiptId: String): Result<Unit> {
        return try {
            database.walletDatabaseQueries.deleteReceipt(receiptId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyAllReceipts(): Result<Map<String, Boolean>> {
        return try {
            val rows = database.walletDatabaseQueries.getAllReceipts().executeAsList()
            val results = rows.associate { row ->
                row.id to (row.receipt_hash != null && row.signature != null && row.salt != null)
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun id.cachet.wallet.db.Consent_receipts.toConsentReceipt(): ConsentReceipt {
        return ConsentReceipt(
            id = id,
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            purpose = purpose,
            predicatesProven = json.decodeFromString(predicates_json),
            rpIdentifier = rp_identifier,
            rpDisplayName = rp_display_name,
            userConsent = json.decodeFromString(consent_json),
            credentialId = credential_id,
            outcome = outcome,
            totalPredicatesCount = total_predicates_count.toInt(),
            receiptHash = receipt_hash,
            signature = signature,
            salt = salt,
            transparencyLogEntry = transparency_log_json?.let { json.decodeFromString(it) }
        )
    }
}
