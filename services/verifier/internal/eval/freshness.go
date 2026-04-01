package eval

import (
	"time"

	"github.com/cachet-id/cachet/generated/go/models"
)

// CheckFreshness determines the freshness status of credentials.
func CheckFreshness(credentials []models.VerifiableCredential) models.VerifyResponseFreshness {
	if len(credentials) == 0 {
		return models.VerifyResponseFreshnessOk
	}

	now := time.Now()
	for _, cred := range credentials {
		// Check expiration
		if cred.ExpirationDate != nil && now.After(*cred.ExpirationDate) {
			return models.VerifyResponseFreshnessExpired
		}
		// Check if issuance is older than 90 days (default TTL)
		if now.Sub(cred.IssuanceDate) > 90*24*time.Hour {
			return models.VerifyResponseFreshnessStale
		}
	}
	return models.VerifyResponseFreshnessOk
}
