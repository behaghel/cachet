package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.repository.PackDefinitionRepository
import kotlin.time.Clock

/**
 * Cache-aside orchestrator for pack definitions.
 *
 * Resolution order:
 * 1. SQLite cache (if fresh within [STALE_THRESHOLD_MS])
 * 2. Bundled pack assets (always available as fallback)
 *
 * On first access or when stale, the cache is seeded/refreshed from bundled assets.
 * Network-fetched full pack definitions are not yet available (backend only serves
 * summaries to mobile); this will be added when the registry exposes a full-pack endpoint.
 */
class PackDefinitionCache(
    private val repository: PackDefinitionRepository,
    private val loadBundled: () -> List<PackDefinition>,
    private val clock: Clock = Clock.System
) {
    companion object {
        const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24h
    }

    suspend fun getAllPacks(): List<PackDefinition> {
        val cached = repository.getAllPacks().getOrNull()
        if (!cached.isNullOrEmpty()) {
            val fetchedAt = repository.getLatestFetchedAt().getOrNull()
            if (fetchedAt != null && !isStale(fetchedAt)) {
                return cached
            }
        }
        // Cache empty or stale — seed from bundled assets
        return seedFromBundled()
    }

    suspend fun getPackById(packId: String): PackDefinition? {
        val cached = repository.getPackById(packId).getOrNull()
        if (cached != null) {
            return cached
        }
        // Try seeding from bundled if cache is empty
        seedFromBundled()
        return repository.getPackById(packId).getOrNull()
    }

    suspend fun seedFromBundled(): List<PackDefinition> {
        val bundled = loadBundled()
        if (bundled.isNotEmpty()) {
            repository.storeAll(bundled)
        }
        return bundled
    }

    private fun isStale(fetchedAtMs: Long): Boolean {
        val now = clock.now().toEpochMilliseconds()
        return (now - fetchedAtMs) > STALE_THRESHOLD_MS
    }
}
