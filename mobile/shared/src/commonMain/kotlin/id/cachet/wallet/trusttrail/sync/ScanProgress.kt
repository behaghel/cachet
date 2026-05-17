package id.cachet.wallet.trusttrail.sync

/**
 * Progress of an inbox scan operation.
 */
data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val paused: Boolean = false,
    val pauseReason: String? = null,
) {
    val isComplete: Boolean get() = total == 0 || scanned >= total

    val fraction: Float get() = if (total == 0) 1.0f else scanned.toFloat() / total.toFloat()
}
