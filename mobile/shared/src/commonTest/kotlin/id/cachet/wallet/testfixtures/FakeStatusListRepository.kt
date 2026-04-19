package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.repository.CachedStatusList
import id.cachet.wallet.domain.repository.StatusListRepository

class FakeStatusListRepository : StatusListRepository {
    private val lists = mutableMapOf<String, CachedStatusList>()

    override suspend fun getStatusList(url: String): Result<CachedStatusList?> {
        return Result.success(lists[url])
    }

    override suspend fun storeStatusList(url: String, encodedList: String, fetchedAt: Long): Result<Unit> {
        lists[url] = CachedStatusList(url, encodedList, fetchedAt)
        return Result.success(Unit)
    }

    override suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit> {
        lists.entries.removeAll { it.value.fetchedAt < cutoffEpochMs }
        return Result.success(Unit)
    }
}
