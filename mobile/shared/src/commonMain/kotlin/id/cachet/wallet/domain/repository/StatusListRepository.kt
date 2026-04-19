package id.cachet.wallet.domain.repository

data class CachedStatusList(
    val url: String,
    val encodedList: String,
    val fetchedAt: Long
)

interface StatusListRepository {
    suspend fun getStatusList(url: String): Result<CachedStatusList?>
    suspend fun storeStatusList(url: String, encodedList: String, fetchedAt: Long): Result<Unit>
    suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit>
}
