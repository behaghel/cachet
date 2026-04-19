package id.cachet.wallet.domain.cache

/**
 * Platform-specific gzip decompression for StatusList2021 bitstrings.
 */
expect fun gzipDecompress(compressed: ByteArray): ByteArray
