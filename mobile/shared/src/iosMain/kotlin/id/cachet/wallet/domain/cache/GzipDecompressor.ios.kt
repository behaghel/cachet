package id.cachet.wallet.domain.cache

actual fun gzipDecompress(compressed: ByteArray): ByteArray {
    // iOS gzip decompression stub — StatusList revocation check will be skipped on iOS
    throw UnsupportedOperationException("Gzip decompression not yet implemented for iOS")
}
