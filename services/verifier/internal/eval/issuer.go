package eval

import "strings"

// matchesIssuer checks if an issuer DID matches any of the accepted patterns.
// Patterns support trailing wildcard: "did:veriff:*" matches "did:veriff:production".
func matchesIssuer(issuer string, patterns []string) bool {
	for _, pattern := range patterns {
		if pattern == issuer {
			return true
		}
		if strings.HasSuffix(pattern, "*") {
			prefix := strings.TrimSuffix(pattern, "*")
			if strings.HasPrefix(issuer, prefix) {
				return true
			}
		}
	}
	return false
}
