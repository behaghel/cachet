package credential

import (
	"crypto/ecdsa"
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// BuildSDJWTCredential constructs an SD-JWT VC from a verified session.
// Selectively disclosable claims: age, nationality, documentType, verified, verification metrics.
// Non-disclosable claims: iss, sub, iat, exp, _sd_alg, cnf, status, vct.
// holderJWK is the holder's public key as a JWK map — embedded in cnf for holder binding.
// If nil, cnf is omitted (no holder binding).
func BuildSDJWTCredential(session veriff.Session, validation veriff.ValidationResult, types []string, issuerKey *ecdsa.PrivateKey, issuerKeyID string, holderJWK map[string]interface{}, statusListIndex int) (string, error) {
	now := time.Now()
	expiration := now.Add(90 * 24 * time.Hour)

	credentialID := fmt.Sprintf("urn:uuid:%s", uuid.New().String())

	// Non-disclosable claims (always visible in the issuer JWT)
	nonDisclosable := map[string]interface{}{
		"iss": "did:veriff:production",
		"sub": "did:example:holder",
		"iat": now.Unix(),
		"exp": expiration.Unix(),
		"jti": credentialID,
		"vct": types,
		"status": map[string]interface{}{
			"id":                   fmt.Sprintf("https://cachet.id/status/1#%d", statusListIndex),
			"type":                 "StatusList2021Entry",
			"statusPurpose":        "revocation",
			"statusListIndex":      fmt.Sprintf("%d", statusListIndex),
			"statusListCredential": "https://cachet.id/status/1",
		},
	}

	// Embed holder's public key for holder binding (KB-JWT verification)
	if holderJWK != nil {
		nonDisclosable["cnf"] = map[string]interface{}{"jwk": holderJWK}
	}

	// Selectively disclosable claims
	age := CalculateAge(session.Person.DateOfBirth)
	sdClaims := map[string]interface{}{
		"age":                  age,
		"nationality":          session.Document.Country,
		"documentType":         session.Document.Type,
		"verified":             true,
		"verificationLevel":    string(models.CredentialSubjectVerificationLevel(validation.QualityLevel)),
		"verificationMethod":   "veriff",
		"overallConfidence":    validation.Confidence,
		"livenessScore":        session.Verification.LivenessScore,
		"documentAuthenticity": session.Document.Authenticity,
		"riskScore":            session.Verification.RiskScore,
	}

	return BuildSDJWT(nonDisclosable, sdClaims, issuerKey, issuerKeyID)
}

// Build constructs a VerifiableCredential as JSON (legacy format).
// Kept for backward compatibility during the transition to SD-JWT.
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
		Issuer:         "did:veriff:production",
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
