package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.crypto.Base64Url
import id.cachet.wallet.domain.repository.StatusListRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fetches, caches, and checks Attestation Status List bitstrings for credential revocation.
 * Backward-compatible with StatusList2021 (same encodedList wire format).
 *
 * Cache hierarchy:
 * 1. SQLite (5-min TTL per spec)
 * 2. HTTP fetch from status list URL
 *
 * On network failure with a cached entry, returns stale cache (best-effort).
 */
class StatusListCache(
    private val repository: StatusListRepository,
    private val httpClient: HttpClient,
    private val clock: Clock = Clock.System
) {
    companion object {
        const val TTL_MS = 5 * 60 * 1000L // 5 minutes per spec
    }

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StatusListCredential(val encodedList: String)

    /**
     * Check if a credential at [index] is revoked in the status list at [statusListUrl].
     * Returns null if the status list cannot be fetched and no cache exists.
     */
    suspend fun isRevoked(statusListUrl: String, index: Int): Boolean? {
        val bits = getBits(statusListUrl) ?: return null
        val byteIdx = index / 8
        val bitIdx = 7 - (index % 8) // MSB first per W3C spec
        if (byteIdx >= bits.size) return null
        return (bits[byteIdx].toInt() shr bitIdx) and 1 == 1
    }

    private suspend fun getBits(url: String): ByteArray? {
        val cached = repository.getStatusList(url).getOrNull()
        if (cached != null && !isExpired(cached.fetchedAt)) {
            return decodeBitstring(cached.encodedList)
        }

        return try {
            val encodedList = fetchEncodedList(url)
            val now = clock.now().toEpochMilliseconds()
            repository.storeStatusList(url, encodedList, now)
            decodeBitstring(encodedList)
        } catch (e: Exception) {
            // Network failed — use stale cache if available
            if (cached != null) decodeBitstring(cached.encodedList) else null
        }
    }

    private suspend fun fetchEncodedList(url: String): String {
        val responseText: String = httpClient.get(url).body()
        val credential = lenientJson.decodeFromString<StatusListCredential>(responseText)
        return credential.encodedList
    }

    private fun decodeBitstring(encoded: String): ByteArray? {
        return try {
            val compressed = Base64Url.decode(encoded)
            gzipDecompress(compressed)
        } catch (e: Exception) {
            null
        }
    }

    private fun isExpired(fetchedAtMs: Long): Boolean {
        val now = clock.now().toEpochMilliseconds()
        return (now - fetchedAtMs) > TTL_MS
    }
}
