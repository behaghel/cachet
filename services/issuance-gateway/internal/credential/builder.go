package credential

import (
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// VerifiableCredential is a W3C Verifiable Credential.
type VerifiableCredential struct {
	Context           []string               `json:"@context"`
	ID                string                 `json:"id"`
	Type              []string               `json:"type"`
	Issuer            string                 `json:"issuer"`
	IssuanceDate      string                 `json:"issuanceDate"`
	ExpirationDate    string                 `json:"expirationDate,omitempty"`
	CredentialSubject map[string]interface{} `json:"credentialSubject"`
	CredentialStatus  *CredentialStatus      `json:"credentialStatus,omitempty"`
}

// CredentialStatus for StatusList2021.
type CredentialStatus struct {
	ID   string `json:"id"`
	Type string `json:"type"`
}

// Response is the credential issuance response.
type Response struct {
	Credential VerifiableCredential `json:"credential"`
	Format     string               `json:"format"`
}

// Build constructs a VerifiableCredential from a verified session.
func Build(session veriff.Session, validation veriff.ValidationResult, types []string, format string) Response {
	now := time.Now()
	expiration := now.Add(90 * 24 * time.Hour)

	vc := VerifiableCredential{
		Context: []string{
			"https://www.w3.org/2018/credentials/v1",
			"https://cachet.id/contexts/identity/v1",
		},
		ID:             fmt.Sprintf("urn:uuid:%s", uuid.New().String()),
		Type:           types,
		Issuer:         "did:web:cachet.id",
		IssuanceDate:   now.Format(time.RFC3339),
		ExpirationDate: expiration.Format(time.RFC3339),
		CredentialSubject: map[string]interface{}{
			"id": "did:example:holder",
			"personalData": map[string]interface{}{
				"age":          CalculateAge(session.Person.DateOfBirth),
				"nationality":  session.Document.Country,
				"documentType": session.Document.Type,
			},
			"verificationLevel":  validation.QualityLevel,
			"verified":           true,
			"verificationMethod": "veriff",
			"verificationMetrics": map[string]interface{}{
				"overallConfidence":    validation.Confidence,
				"livenessScore":        session.Verification.LivenessScore,
				"documentAuthenticity": session.Document.Authenticity,
				"riskScore":            session.Verification.RiskScore,
				"sessionTimestamp":     session.Verification.Timestamp,
			},
			"evidence": []map[string]interface{}{
				{
					"type":      "VeriffVerification",
					"sessionId": session.SessionID,
					"verifier":  "did:veriff:production",
					"status":    session.Status,
				},
			},
		},
		CredentialStatus: &CredentialStatus{
			ID:   fmt.Sprintf("https://cachet.id/status/1#%s", uuid.New().String()),
			Type: "StatusList2021Entry",
		},
	}

	return Response{Credential: vc, Format: format}
}

// CalculateAge returns age in years from a "YYYY-MM-DD" date of birth.
func CalculateAge(dobStr string) int {
	dob, err := time.Parse("2006-01-02", dobStr)
	if err != nil {
		return 0
	}
	now := time.Now()
	age := now.Year() - dob.Year()
	if now.Month() < dob.Month() || (now.Month() == dob.Month() && now.Day() < dob.Day()) {
		age--
	}
	return age
}
