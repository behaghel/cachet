package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.repository.PackDefinitionRepository

class FakePackDefinitionRepository : PackDefinitionRepository {
    private val packs = mutableMapOf<String, PackDefinition>()
    private var fetchedAt: Long? = null

    override suspend fun getPackById(packId: String): Result<PackDefinition?> {
        return Result.success(packs[packId])
    }

    override suspend fun getAllPacks(): Result<List<PackDefinition>> {
        return Result.success(packs.values.toList())
    }

    override suspend fun storePack(pack: PackDefinition): Result<Unit> {
        packs[pack.id] = pack
        fetchedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return Result.success(Unit)
    }

    override suspend fun storeAll(packs: List<PackDefinition>): Result<Unit> {
        for (pack in packs) {
            this.packs[pack.id] = pack
        }
        fetchedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return Result.success(Unit)
    }

    override suspend fun deleteAll(): Result<Unit> {
        packs.clear()
        fetchedAt = null
        return Result.success(Unit)
    }

    override suspend fun getLatestFetchedAt(): Result<Long?> {
        return Result.success(fetchedAt)
    }
}
