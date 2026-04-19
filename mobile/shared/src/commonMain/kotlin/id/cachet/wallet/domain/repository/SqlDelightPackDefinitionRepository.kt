package id.cachet.wallet.domain.repository

import id.cachet.wallet.db.WalletDatabase
import id.cachet.wallet.domain.model.PackDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SqlDelightPackDefinitionRepository(
    private val database: WalletDatabase
) : PackDefinitionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPackById(packId: String): Result<PackDefinition?> {
        return try {
            val row = database.walletDatabaseQueries.getPackById(packId).executeAsOneOrNull()
            Result.success(row?.let { json.decodeFromString<PackDefinition>(it.pack_json) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllPacks(): Result<List<PackDefinition>> {
        return try {
            val rows = database.walletDatabaseQueries.getAllPacks().executeAsList()
            Result.success(rows.map { json.decodeFromString<PackDefinition>(it.pack_json) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun storePack(pack: PackDefinition): Result<Unit> {
        return try {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            database.walletDatabaseQueries.upsertPack(
                pack_id = pack.id,
                version = pack.version,
                name = pack.name,
                pack_json = json.encodeToString(pack),
                fetched_at = now
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun storeAll(packs: List<PackDefinition>): Result<Unit> {
        return try {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            database.walletDatabaseQueries.transaction {
                for (pack in packs) {
                    database.walletDatabaseQueries.upsertPack(
                        pack_id = pack.id,
                        version = pack.version,
                        name = pack.name,
                        pack_json = json.encodeToString(pack),
                        fetched_at = now
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAll(): Result<Unit> {
        return try {
            database.walletDatabaseQueries.deleteAllPacks()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLatestFetchedAt(): Result<Long?> {
        return try {
            val row = database.walletDatabaseQueries.getLatestPackFetchedAt().executeAsOneOrNull()
            Result.success(row?.MAX)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
