package eval

import "github.com/cachet-id/cachet/generated/go/models"

// ResolveClaim extracts a named claim value from a credential subject.
func ResolveClaim(subject models.CredentialSubject, claimName string) (interface{}, bool) {
	switch claimName {
	case "age":
		if subject.PersonalData != nil && subject.PersonalData.Age != nil {
			return *subject.PersonalData.Age, true
		}
	case "verified":
		if subject.Verified != nil {
			return *subject.Verified, true
		}
	case "nationality":
		if subject.PersonalData != nil && subject.PersonalData.Nationality != nil {
			return *subject.PersonalData.Nationality, true
		}
	case "documentType":
		if subject.PersonalData != nil && subject.PersonalData.DocumentType != nil {
			return *subject.PersonalData.DocumentType, true
		}
	case "overallConfidence":
		if subject.VerificationMetrics != nil && subject.VerificationMetrics.OverallConfidence != nil {
			return *subject.VerificationMetrics.OverallConfidence, true
		}
	case "livenessScore":
		if subject.VerificationMetrics != nil && subject.VerificationMetrics.LivenessScore != nil {
			return *subject.VerificationMetrics.LivenessScore, true
		}
	}
	return nil, false
}
