package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.PackDefinition

interface PackDefinitionRepository {
    suspend fun getPackById(packId: String): Result<PackDefinition?>
    suspend fun getAllPacks(): Result<List<PackDefinition>>
    suspend fun storePack(pack: PackDefinition): Result<Unit>
    suspend fun storeAll(packs: List<PackDefinition>): Result<Unit>
    suspend fun deleteAll(): Result<Unit>
    suspend fun getLatestFetchedAt(): Result<Long?>
}
