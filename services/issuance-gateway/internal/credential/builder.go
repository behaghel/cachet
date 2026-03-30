package credential

import (
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// Build constructs a VerifiableCredential from a verified session.
func Build(session veriff.Session, validation veriff.ValidationResult, types []string, format string) models.CredentialResponse {
	now := time.Now()
	expiration := now.Add(90 * 24 * time.Hour)

	age := CalculateAge(session.Person.DateOfBirth)
	verified := true
	verificationMethod := "veriff"
	verificationLevel := models.CredentialSubjectVerificationLevel(validation.QualityLevel)

	vc := models.VerifiableCredential{
		Context: []string{
			"https://www.w3.org/2018/credentials/v1",
			"https://cachet.id/contexts/identity/v1",
		},
		Id:             fmt.Sprintf("urn:uuid:%s", uuid.New().String()),
		Type:           types,
		Issuer:         "did:web:cachet.id",
		IssuanceDate:   now,
		ExpirationDate: &expiration,
		CredentialSubject: models.CredentialSubject{
			Id:       "did:example:holder",
			Verified: &verified,
			PersonalData: &struct {
				Age          *int    `json:"age,omitempty"`
				DocumentType *string `json:"documentType,omitempty"`
				Nationality  *string `json:"nationality,omitempty"`
			}{
				Age:          &age,
				Nationality:  &session.Document.Country,
				DocumentType: &session.Document.Type,
			},
			VerificationLevel:  &verificationLevel,
			VerificationMethod: &verificationMethod,
			VerificationMetrics: &struct {
				DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
				LivenessScore        *float64   `json:"livenessScore,omitempty"`
				OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
				RiskScore            *float64   `json:"riskScore,omitempty"`
				SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
			}{
				OverallConfidence:    &validation.Confidence,
				LivenessScore:        &session.Verification.LivenessScore,
				DocumentAuthenticity: &session.Document.Authenticity,
				RiskScore:            &session.Verification.RiskScore,
			},
			Evidence: &[]struct {
				SessionId *string `json:"sessionId,omitempty"`
				Status    *string `json:"status,omitempty"`
				Type      *string `json:"type,omitempty"`
				Verifier  *string `json:"verifier,omitempty"`
			}{
				{
					Type:      ptr("VeriffVerification"),
					SessionId: &session.SessionID,
					Verifier:  ptr("did:veriff:production"),
					Status:    &session.Status,
				},
			},
		},
		CredentialStatus: &models.CredentialStatus{
			Id:   fmt.Sprintf("https://cachet.id/status/1#%s", uuid.New().String()),
			Type: models.StatusList2021Entry,
		},
	}

	return models.CredentialResponse{Credential: vc, Format: format}
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

func ptr(s string) *string { return &s }
