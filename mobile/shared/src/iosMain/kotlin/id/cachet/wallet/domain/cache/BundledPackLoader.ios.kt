package id.cachet.wallet.domain.cache

import id.cachet.wallet.domain.model.PackDefinition
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual class BundledPackLoader {

    private val json = Json { ignoreUnknownKeys = true }

    actual fun loadBundledPacks(): List<PackDefinition> {
        val bundle = NSBundle.mainBundle
        val packNames = listOf(
            "identity-basic",
            "safe-seller.base",
            "childcare-readiness.base",
            "childcare-readiness.ee",
            "childcare-readiness.es",
            "childcare-readiness.fr"
        )
        return packNames.mapNotNull { name ->
            try {
                val path = bundle.pathForResource(name, "json") ?: return@mapNotNull null
                val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
                    ?: return@mapNotNull null
                json.decodeFromString<PackDefinition>(content)
            } catch (e: Exception) {
                null
            }
        }
    }
}
