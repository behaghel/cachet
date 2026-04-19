package id.cachet.wallet.domain.repository

import id.cachet.wallet.db.WalletDatabase

class SqlDelightStatusListRepository(
    private val database: WalletDatabase
) : StatusListRepository {

    override suspend fun getStatusList(url: String): Result<CachedStatusList?> {
        return try {
            val row = database.walletDatabaseQueries.getStatusList(url).executeAsOneOrNull()
            Result.success(row?.let {
                CachedStatusList(
                    url = it.status_list_url,
                    encodedList = it.encoded_list,
                    fetchedAt = it.fetched_at
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun storeStatusList(url: String, encodedList: String, fetchedAt: Long): Result<Unit> {
        return try {
            database.walletDatabaseQueries.upsertStatusList(
                status_list_url = url,
                encoded_list = encodedList,
                fetched_at = fetchedAt
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpired(cutoffEpochMs: Long): Result<Unit> {
        return try {
            database.walletDatabaseQueries.deleteExpiredStatusLists(cutoffEpochMs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
