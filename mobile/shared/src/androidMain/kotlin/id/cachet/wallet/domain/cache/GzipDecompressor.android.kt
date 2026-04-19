package id.cachet.wallet.domain.cache

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

actual fun gzipDecompress(compressed: ByteArray): ByteArray {
    return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
}
