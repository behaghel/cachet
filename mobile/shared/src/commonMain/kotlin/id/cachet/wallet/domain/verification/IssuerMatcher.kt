package id.cachet.wallet.domain.verification

/**
 * Matches issuer DIDs against accepted patterns.
 * Supports exact match and trailing wildcard (e.g., "did:veriff:*" matches "did:veriff:production").
 *
 * Port of Go `services/verifier/internal/eval/issuer.go`.
 */
object IssuerMatcher {

    fun matches(issuer: String, patterns: List<String>): Boolean {
        for (pattern in patterns) {
            if (pattern == issuer) return true
            if (pattern.endsWith("*")) {
                val prefix = pattern.removeSuffix("*")
                if (issuer.startsWith(prefix)) return true
            }
        }
        return false
    }
}
