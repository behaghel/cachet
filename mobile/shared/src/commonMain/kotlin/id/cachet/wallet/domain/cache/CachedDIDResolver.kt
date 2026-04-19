package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.crypto.DIDResolver
import id.cachet.wallet.domain.repository.DIDDocumentRepository
import kotlin.time.Clock

/**
 * Caching decorator around [DIDResolver].
 *
 * Checks SQLite cache first (24h TTL). On cache miss or expiry,
 * delegates to the network resolver and stores the result.
 * On network failure with a stale cache entry, returns the stale value.
 */
class CachedDIDResolver(
    private val delegate: DIDResolver,
    private val repository: DIDDocumentRepository,
    private val clock: Clock = Clock.System
) {
    companion object {
        const val TTL_MS = 24 * 60 * 60 * 1000L // 24h
    }

    suspend fun resolvePublicKeyJWK(did: String, kid: String? = null): String {
        // Check cache
        val cached = repository.getDocument(did).getOrNull()
        if (cached != null && !isExpired(cached.fetchedAt)) {
            return DIDResolver.extractPublicKeyJWK(cached.documentJson, did, kid)
        }

        // Cache miss or expired — try network
        return try {
            val documentJson = delegate.resolveDocument(did)
            val now = clock.now().toEpochMilliseconds()
            repository.storeDocument(did, documentJson, now)
            DIDResolver.extractPublicKeyJWK(documentJson, did, kid)
        } catch (e: Exception) {
            // Network failed — use stale cache if available
            if (cached != null) {
                DIDResolver.extractPublicKeyJWK(cached.documentJson, did, kid)
            } else {
                throw e
            }
        }
    }

    private fun isExpired(fetchedAtMs: Long): Boolean {
        val now = clock.now().toEpochMilliseconds()
        return (now - fetchedAtMs) > TTL_MS
    }
}
