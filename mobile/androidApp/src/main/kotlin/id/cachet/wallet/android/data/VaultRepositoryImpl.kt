package id.cachet.wallet.android.data

import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.domain.model.VaultArtifact
import id.cachet.wallet.domain.model.VaultPredicate
import id.cachet.wallet.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class VaultRepositoryImpl(
    private val database: WalletDatabase
) : VaultRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun upsertArtifacts(artifacts: List<VaultArtifact>) {
        withContext(Dispatchers.IO) {
            artifacts.forEach { artifact ->
                database.walletDatabaseQueries.insertVaultArtifact(
                    artifact_id = artifact.id,
                    artifact_type = artifact.type,
                    source = artifact.source,
                    payload_json = json.encodeToString(JsonElement.serializer(), artifact.payload),
                    created_at = artifact.createdAt.epochSeconds
                )
            }
        }
    }

    override suspend fun upsertPredicates(predicates: List<VaultPredicate>) {
        withContext(Dispatchers.IO) {
            predicates.forEach { predicate ->
                database.walletDatabaseQueries.insertVaultPredicate(
                    predicate_id = predicate.id,
                    artifact_id = predicate.artifact?.id,
                    predicate_key = predicate.key,
                    predicate_value = predicate.value,
                    proof_type = predicate.proofType,
                    issued_at = predicate.issuedAt.epochSeconds,
                    expires_at = predicate.expiresAt?.epochSeconds
                )
                predicate.artifact?.let { artifact ->
                    database.walletDatabaseQueries.insertVaultArtifact(
                        artifact_id = artifact.id,
                        artifact_type = artifact.type,
                        source = artifact.source,
                        payload_json = json.encodeToString(JsonElement.serializer(), artifact.payload),
                        created_at = artifact.createdAt.epochSeconds
                    )
                }
            }
        }
    }

    override suspend fun getAllPredicates(): List<VaultPredicate> {
        return withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.getAllVaultPredicates()
                .executeAsList()
                .map { row ->
                    val artifact = row.artifact_id?.let {
                        VaultArtifact(
                            id = it,
                            type = row.artifact_type ?: "unknown",
                            source = row.source ?: "unknown",
                            payload = row.payload_json?.let { payload ->
                                json.decodeFromString(JsonElement.serializer(), payload)
                            } ?: json.parseToJsonElement("{}"),
                            createdAt = Instant.fromEpochSeconds(row.artifact_created_at ?: row.issued_at)
                        )
                    }

                    VaultPredicate(
                        id = row.predicate_id,
                        key = row.predicate_key,
                        value = row.predicate_value,
                        proofType = row.proof_type,
                        issuedAt = Instant.fromEpochSeconds(row.issued_at),
                        expiresAt = row.expires_at?.let { Instant.fromEpochSeconds(it) },
                        artifact = artifact
                    )
                }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.walletDatabaseQueries.deleteVaultData()
        }
    }
}
