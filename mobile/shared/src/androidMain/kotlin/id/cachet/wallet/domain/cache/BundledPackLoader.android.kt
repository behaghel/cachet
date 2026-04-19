package id.cachet.wallet.domain.cache

import android.content.Context
import id.cachet.wallet.domain.model.PackDefinition
import kotlinx.serialization.json.Json

actual class BundledPackLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    actual fun loadBundledPacks(): List<PackDefinition> {
        val assetFiles = context.assets.list("packs") ?: return emptyList()
        return assetFiles
            .filter { it.endsWith(".json") }
            .mapNotNull { filename ->
                try {
                    val content = context.assets.open("packs/$filename").bufferedReader().use { it.readText() }
                    json.decodeFromString<PackDefinition>(content)
                } catch (e: Exception) {
                    null
                }
            }
    }
}
