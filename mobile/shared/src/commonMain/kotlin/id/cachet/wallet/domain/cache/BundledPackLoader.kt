package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.model.PackDefinition

/**
 * Platform-specific loader for pack definitions bundled as app assets.
 * These serve as the last-resort fallback when both cache and network are unavailable.
 */
expect class BundledPackLoader {
    fun loadBundledPacks(): List<PackDefinition>
}
