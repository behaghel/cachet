package id.cachet.wallet.trusttrail.dkim

/**
 * Parses RFC 8601 Authentication-Results headers to determine
 * whether the receiving MTA reported DKIM verification as passed.
 */
object AuthenticationResultsParser {

    private val dkimPassPattern = Regex("""(?i)\bdkim\s*=\s*pass\b""")

    /**
     * Check if a single Authentication-Results header contains dkim=pass.
     */
    fun hasDkimPass(header: String): Boolean {
        return dkimPassPattern.containsMatchIn(header)
    }

    /**
     * Check if any of the Authentication-Results headers contains dkim=pass.
     * An email can have multiple AR headers (one per MTA hop).
     */
    fun anyDkimPass(headers: List<String>): Boolean {
        return headers.any { hasDkimPass(it) }
    }
}
