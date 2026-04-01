package eval

import (
	"testing"
	"time"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- helpers ---

func ptrBool(b bool) *bool          { return &b }
func ptrInt(i int) *int             { return &i }
func ptrFloat64(f float64) *float64 { return &f }
func ptrString(s string) *string    { return &s }

func makeCredential(issuer string, subject models.CredentialSubject) models.VerifiableCredential {
	return models.VerifiableCredential{
		Id:                "urn:uuid:test",
		Issuer:            issuer,
		IssuanceDate:      time.Now(),
		CredentialSubject: subject,
		Context:           []string{"https://www.w3.org/2018/credentials/v1"},
		Type:              []string{"VerifiableCredential"},
	}
}

// --- SDJWTEvaluator tests ---

func TestSDJWT_AgeGte18_Satisfied(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "age.ge.18",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:production"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(25)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, "age.ge.18", result.PredicateId)
	assert.Equal(t, models.Satisfied, result.Status)
	assert.Nil(t, result.Reason)
}

func TestSDJWT_AgeGte18_Failed(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "age.ge.18",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:production"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(17)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, "age.ge.18", result.PredicateId)
	assert.Equal(t, models.Failed, result.Status)
	require.NotNil(t, result.Reason)
	assert.Contains(t, *result.Reason, "expected >= 18")
}

func TestSDJWT_BooleanTrue_Satisfied(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "identity.verified",
		Claim:           "verified",
		Operator:        models.Boolean,
		Value:           true,
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id:       "did:key:user1",
		Verified: ptrBool(true),
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, models.Satisfied, result.Status)
}

func TestSDJWT_BooleanFalse_Failed(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "identity.verified",
		Claim:           "verified",
		Operator:        models.Boolean,
		Value:           true,
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id:       "did:key:user1",
		Verified: ptrBool(false),
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, models.Failed, result.Status)
	require.NotNil(t, result.Reason)
	assert.Contains(t, *result.Reason, "expected true, got false")
}

func TestSDJWT_NoCredentialFromAcceptedIssuer(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "age.ge.18",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:production"},
	}

	cred := makeCredential("did:other:unknown", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(25)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, models.NoCredential, result.Status)
	require.NotNil(t, result.Reason)
	assert.Contains(t, *result.Reason, "no credential from an accepted issuer")
}

func TestSDJWT_EqualEqual_Satisfied(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "doc.type.passport",
		Claim:           "documentType",
		Operator:        models.EqualEqual,
		Value:           "passport",
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{DocumentType: ptrString("passport")},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, models.Satisfied, result.Status)
}

func TestSDJWT_GreaterThan(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "confidence.gt.0.8",
		Claim:           "overallConfidence",
		Operator:        models.GreaterThan,
		Value:           float64(0.8),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		VerificationMetrics: &struct {
			DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
			LivenessScore        *float64   `json:"livenessScore,omitempty"`
			OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
			RiskScore            *float64   `json:"riskScore,omitempty"`
			SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
		}{OverallConfidence: ptrFloat64(0.9)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})
	assert.Equal(t, models.Satisfied, result.Status)

	// Edge case: exactly 0.8 should fail for >
	cred.CredentialSubject.VerificationMetrics.OverallConfidence = ptrFloat64(0.8)
	result = evaluator.Evaluate(pred, []models.VerifiableCredential{cred})
	assert.Equal(t, models.Failed, result.Status)
}

func TestSDJWT_LessThan(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "risk.lt.0.5",
		Claim:           "livenessScore",
		Operator:        models.LessThan,
		Value:           float64(0.5),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		VerificationMetrics: &struct {
			DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
			LivenessScore        *float64   `json:"livenessScore,omitempty"`
			OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
			RiskScore            *float64   `json:"riskScore,omitempty"`
			SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
		}{LivenessScore: ptrFloat64(0.3)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})
	assert.Equal(t, models.Satisfied, result.Status)

	// Exactly 0.5 should fail for <
	cred.CredentialSubject.VerificationMetrics.LivenessScore = ptrFloat64(0.5)
	result = evaluator.Evaluate(pred, []models.VerifiableCredential{cred})
	assert.Equal(t, models.Failed, result.Status)
}

func TestSDJWT_LessThanEqual(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "risk.lte.0.5",
		Claim:           "livenessScore",
		Operator:        models.LessThanEqual,
		Value:           float64(0.5),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		VerificationMetrics: &struct {
			DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
			LivenessScore        *float64   `json:"livenessScore,omitempty"`
			OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
			RiskScore            *float64   `json:"riskScore,omitempty"`
			SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
		}{LivenessScore: ptrFloat64(0.5)},
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})
	assert.Equal(t, models.Satisfied, result.Status)
}

func TestSDJWT_EmptyCredentials(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "age.ge.18",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{})

	assert.Equal(t, models.NoCredential, result.Status)
}

func TestSDJWT_ClaimNotPresent(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "age.ge.18",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.SdJwt,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	// Credential with matching issuer but no personalData
	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
	})

	evaluator := &SDJWTEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{cred})

	assert.Equal(t, models.NoCredential, result.Status)
}

// --- Issuer matching tests ---

func TestMatchesIssuer_ExactMatch(t *testing.T) {
	assert.True(t, matchesIssuer("did:veriff:production", []string{"did:veriff:production"}))
}

func TestMatchesIssuer_WildcardMatch(t *testing.T) {
	assert.True(t, matchesIssuer("did:veriff:production", []string{"did:veriff:*"}))
	assert.True(t, matchesIssuer("did:veriff:staging", []string{"did:veriff:*"}))
}

func TestMatchesIssuer_NoMatch(t *testing.T) {
	assert.False(t, matchesIssuer("did:other:production", []string{"did:veriff:*"}))
	assert.False(t, matchesIssuer("did:other:production", []string{"did:veriff:production"}))
}

func TestMatchesIssuer_MultiplePatterns(t *testing.T) {
	patterns := []string{"did:veriff:production", "did:cachet:*"}
	assert.True(t, matchesIssuer("did:veriff:production", patterns))
	assert.True(t, matchesIssuer("did:cachet:anything", patterns))
	assert.False(t, matchesIssuer("did:other:foo", patterns))
}

func TestMatchesIssuer_EmptyPatterns(t *testing.T) {
	assert.False(t, matchesIssuer("did:veriff:production", []string{}))
}

// --- Claim resolver tests ---

func TestResolveClaim_Age(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(25)},
	}

	val, ok := ResolveClaim(subject, "age")
	assert.True(t, ok)
	assert.Equal(t, 25, val)
}

func TestResolveClaim_Verified(t *testing.T) {
	subject := models.CredentialSubject{
		Id:       "did:key:user1",
		Verified: ptrBool(true),
	}

	val, ok := ResolveClaim(subject, "verified")
	assert.True(t, ok)
	assert.Equal(t, true, val)
}

func TestResolveClaim_Nationality(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Nationality: ptrString("EE")},
	}

	val, ok := ResolveClaim(subject, "nationality")
	assert.True(t, ok)
	assert.Equal(t, "EE", val)
}

func TestResolveClaim_DocumentType(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{DocumentType: ptrString("passport")},
	}

	val, ok := ResolveClaim(subject, "documentType")
	assert.True(t, ok)
	assert.Equal(t, "passport", val)
}

func TestResolveClaim_OverallConfidence(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		VerificationMetrics: &struct {
			DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
			LivenessScore        *float64   `json:"livenessScore,omitempty"`
			OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
			RiskScore            *float64   `json:"riskScore,omitempty"`
			SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
		}{OverallConfidence: ptrFloat64(0.95)},
	}

	val, ok := ResolveClaim(subject, "overallConfidence")
	assert.True(t, ok)
	assert.Equal(t, 0.95, val)
}

func TestResolveClaim_LivenessScore(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		VerificationMetrics: &struct {
			DocumentAuthenticity *float64   `json:"documentAuthenticity,omitempty"`
			LivenessScore        *float64   `json:"livenessScore,omitempty"`
			OverallConfidence    *float64   `json:"overallConfidence,omitempty"`
			RiskScore            *float64   `json:"riskScore,omitempty"`
			SessionTimestamp     *time.Time `json:"sessionTimestamp,omitempty"`
		}{LivenessScore: ptrFloat64(0.88)},
	}

	val, ok := ResolveClaim(subject, "livenessScore")
	assert.True(t, ok)
	assert.Equal(t, 0.88, val)
}

func TestResolveClaim_UnknownClaim(t *testing.T) {
	subject := models.CredentialSubject{Id: "did:key:user1"}
	val, ok := ResolveClaim(subject, "unknownField")
	assert.False(t, ok)
	assert.Nil(t, val)
}

func TestResolveClaim_NilPersonalData(t *testing.T) {
	subject := models.CredentialSubject{Id: "did:key:user1"}
	val, ok := ResolveClaim(subject, "age")
	assert.False(t, ok)
	assert.Nil(t, val)
}

func TestResolveClaim_NilVerificationMetrics(t *testing.T) {
	subject := models.CredentialSubject{Id: "did:key:user1"}
	val, ok := ResolveClaim(subject, "overallConfidence")
	assert.False(t, ok)
	assert.Nil(t, val)
}

func TestResolveClaim_NilVerified(t *testing.T) {
	subject := models.CredentialSubject{Id: "did:key:user1"}
	val, ok := ResolveClaim(subject, "verified")
	assert.False(t, ok)
	assert.Nil(t, val)
}

func TestResolveClaim_NilAgeInsidePersonalData(t *testing.T) {
	subject := models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{},
	}
	val, ok := ResolveClaim(subject, "age")
	assert.False(t, ok)
	assert.Nil(t, val)
}

// --- UnsupportedEvaluator tests ---

func TestUnsupportedEvaluator_ReturnsNotEvaluable(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "zk.proof",
		Claim:           "age",
		Operator:        models.GreaterThanEqual,
		Value:           float64(18),
		ProofType:       models.ZkSnark,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	evaluator := &UnsupportedEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{})

	assert.Equal(t, "zk.proof", result.PredicateId)
	assert.Equal(t, models.NotEvaluable, result.Status)
	require.NotNil(t, result.Reason)
	assert.Contains(t, *result.Reason, "zk-snark")
	assert.Contains(t, *result.Reason, "not yet supported")
}

func TestUnsupportedEvaluator_BBS(t *testing.T) {
	pred := models.PredicateDefinition{
		Id:              "bbs.proof",
		Claim:           "verified",
		Operator:        models.Boolean,
		Value:           true,
		ProofType:       models.VcBbs,
		IssuersAccepted: []string{"did:veriff:*"},
	}

	evaluator := &UnsupportedEvaluator{}
	result := evaluator.Evaluate(pred, []models.VerifiableCredential{})

	assert.Equal(t, models.NotEvaluable, result.Status)
	require.NotNil(t, result.Reason)
	assert.Contains(t, *result.Reason, "vc-bbs")
}

// --- Freshness tests ---

func TestCheckFreshness_EmptyCredentials(t *testing.T) {
	result := CheckFreshness([]models.VerifiableCredential{})
	assert.Equal(t, models.VerifyResponseFreshnessOk, result)
}

func TestCheckFreshness_FreshCredential(t *testing.T) {
	cred := models.VerifiableCredential{
		Id:           "urn:uuid:fresh",
		Issuer:       "did:veriff:production",
		IssuanceDate: time.Now().Add(-24 * time.Hour), // 1 day old
		Context:      []string{"https://www.w3.org/2018/credentials/v1"},
		Type:         []string{"VerifiableCredential"},
	}
	result := CheckFreshness([]models.VerifiableCredential{cred})
	assert.Equal(t, models.VerifyResponseFreshnessOk, result)
}

func TestCheckFreshness_StaleCredential(t *testing.T) {
	cred := models.VerifiableCredential{
		Id:           "urn:uuid:stale",
		Issuer:       "did:veriff:production",
		IssuanceDate: time.Now().Add(-100 * 24 * time.Hour), // 100 days old
		Context:      []string{"https://www.w3.org/2018/credentials/v1"},
		Type:         []string{"VerifiableCredential"},
	}
	result := CheckFreshness([]models.VerifiableCredential{cred})
	assert.Equal(t, models.VerifyResponseFreshnessStale, result)
}

func TestCheckFreshness_ExpiredCredential(t *testing.T) {
	expired := time.Now().Add(-1 * time.Hour) // expired 1 hour ago
	cred := models.VerifiableCredential{
		Id:             "urn:uuid:expired",
		Issuer:         "did:veriff:production",
		IssuanceDate:   time.Now().Add(-30 * 24 * time.Hour),
		ExpirationDate: &expired,
		Context:        []string{"https://www.w3.org/2018/credentials/v1"},
		Type:           []string{"VerifiableCredential"},
	}
	result := CheckFreshness([]models.VerifiableCredential{cred})
	assert.Equal(t, models.VerifyResponseFreshnessExpired, result)
}

func TestCheckFreshness_NotYetExpired(t *testing.T) {
	future := time.Now().Add(30 * 24 * time.Hour) // expires in 30 days
	cred := models.VerifiableCredential{
		Id:             "urn:uuid:valid",
		Issuer:         "did:veriff:production",
		IssuanceDate:   time.Now().Add(-10 * 24 * time.Hour),
		ExpirationDate: &future,
		Context:        []string{"https://www.w3.org/2018/credentials/v1"},
		Type:           []string{"VerifiableCredential"},
	}
	result := CheckFreshness([]models.VerifiableCredential{cred})
	assert.Equal(t, models.VerifyResponseFreshnessOk, result)
}

// --- Full Evaluate function tests ---

func TestEvaluate_MixedPredicates(t *testing.T) {
	falseVal := false
	pack := models.PackDefinition{
		Id:      "pack.test@1.0.0",
		Name:    "Test Pack",
		Version: "1.0.0",
		Purpose: "testing",
		Badge: models.BadgeDefinition{
			Label: "Test Badge",
			Ttl:   "P90D",
		},
		Predicates: []models.PredicateDefinition{
			{
				Id:              "age.ge.18",
				Claim:           "age",
				Operator:        models.GreaterThanEqual,
				Value:           float64(18),
				ProofType:       models.SdJwt,
				IssuersAccepted: []string{"did:veriff:*"},
				// Required is nil -> defaults to true
			},
			{
				Id:              "identity.verified",
				Claim:           "verified",
				Operator:        models.Boolean,
				Value:           true,
				ProofType:       models.SdJwt,
				IssuersAccepted: []string{"did:veriff:*"},
				// Required is nil -> defaults to true
			},
			{
				Id:              "zk.proof",
				Claim:           "age",
				Operator:        models.GreaterThanEqual,
				Value:           float64(18),
				ProofType:       models.ZkSnark,
				IssuersAccepted: []string{"did:veriff:*"},
				Required:        &falseVal, // optional
			},
		},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(25)},
		Verified: ptrBool(true),
	})

	results, summary := Evaluate(pack, []models.VerifiableCredential{cred})

	require.Len(t, results, 3)

	// First predicate: age >= 18 satisfied
	assert.Equal(t, "age.ge.18", results[0].PredicateId)
	assert.Equal(t, models.Satisfied, results[0].Status)

	// Second predicate: verified == true satisfied
	assert.Equal(t, "identity.verified", results[1].PredicateId)
	assert.Equal(t, models.Satisfied, results[1].Status)

	// Third predicate: zk-snark not evaluable
	assert.Equal(t, "zk.proof", results[2].PredicateId)
	assert.Equal(t, models.NotEvaluable, results[2].Status)

	// Summary: 2 required satisfied out of 2, 0 optional satisfied out of 1
	assert.Equal(t, 2, summary.RequiredTotal)
	assert.Equal(t, 2, summary.RequiredSatisfied)
	require.NotNil(t, summary.OptionalTotal)
	assert.Equal(t, 1, *summary.OptionalTotal)
	require.NotNil(t, summary.OptionalSatisfied)
	assert.Equal(t, 0, *summary.OptionalSatisfied)
	assert.True(t, summary.BadgeGranted)
}

func TestEvaluate_BadgeNotGranted(t *testing.T) {
	pack := models.PackDefinition{
		Id:      "pack.test@1.0.0",
		Name:    "Test Pack",
		Version: "1.0.0",
		Purpose: "testing",
		Badge: models.BadgeDefinition{
			Label: "Test Badge",
			Ttl:   "P90D",
		},
		Predicates: []models.PredicateDefinition{
			{
				Id:              "age.ge.18",
				Claim:           "age",
				Operator:        models.GreaterThanEqual,
				Value:           float64(18),
				ProofType:       models.SdJwt,
				IssuersAccepted: []string{"did:veriff:*"},
			},
			{
				Id:              "identity.verified",
				Claim:           "verified",
				Operator:        models.Boolean,
				Value:           true,
				ProofType:       models.SdJwt,
				IssuersAccepted: []string{"did:veriff:*"},
			},
		},
	}

	// Credential with age 17 (fails age check)
	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(17)},
		Verified: ptrBool(true),
	})

	results, summary := Evaluate(pack, []models.VerifiableCredential{cred})

	require.Len(t, results, 2)
	assert.Equal(t, models.Failed, results[0].Status)
	assert.Equal(t, models.Satisfied, results[1].Status)

	assert.Equal(t, 2, summary.RequiredTotal)
	assert.Equal(t, 1, summary.RequiredSatisfied)
	assert.False(t, summary.BadgeGranted)
}

func TestEvaluate_EmptyPredicates(t *testing.T) {
	pack := models.PackDefinition{
		Id:         "pack.empty@1.0.0",
		Name:       "Empty Pack",
		Version:    "1.0.0",
		Purpose:    "testing",
		Badge:      models.BadgeDefinition{Label: "Empty", Ttl: "P1D"},
		Predicates: []models.PredicateDefinition{},
	}

	results, summary := Evaluate(pack, []models.VerifiableCredential{})

	assert.Empty(t, results)
	assert.Equal(t, 0, summary.RequiredTotal)
	assert.Equal(t, 0, summary.RequiredSatisfied)
	assert.True(t, summary.BadgeGranted) // 0 == 0
}

func TestEvaluate_RequiredExplicitlyTrue(t *testing.T) {
	trueVal := true
	pack := models.PackDefinition{
		Id:      "pack.explicit@1.0.0",
		Name:    "Explicit Required",
		Version: "1.0.0",
		Purpose: "testing",
		Badge:   models.BadgeDefinition{Label: "Badge", Ttl: "P1D"},
		Predicates: []models.PredicateDefinition{
			{
				Id:              "age.ge.18",
				Claim:           "age",
				Operator:        models.GreaterThanEqual,
				Value:           float64(18),
				ProofType:       models.SdJwt,
				IssuersAccepted: []string{"did:veriff:*"},
				Required:        &trueVal,
			},
		},
	}

	cred := makeCredential("did:veriff:production", models.CredentialSubject{
		Id: "did:key:user1",
		PersonalData: &struct {
			Age          *int    `json:"age,omitempty"`
			DocumentType *string `json:"documentType,omitempty"`
			Nationality  *string `json:"nationality,omitempty"`
		}{Age: ptrInt(20)},
	})

	_, summary := Evaluate(pack, []models.VerifiableCredential{cred})
	assert.Equal(t, 1, summary.RequiredTotal)
	assert.Equal(t, 1, summary.RequiredSatisfied)
	assert.True(t, summary.BadgeGranted)
}

// --- evaluatorFor dispatch tests ---

func TestEvaluatorFor_SdJwt(t *testing.T) {
	e := evaluatorFor(models.SdJwt)
	_, ok := e.(*SDJWTEvaluator)
	assert.True(t, ok)
}

func TestEvaluatorFor_Unsupported(t *testing.T) {
	e := evaluatorFor(models.VcBbs)
	_, ok := e.(*UnsupportedEvaluator)
	assert.True(t, ok)

	e = evaluatorFor(models.ZkSnark)
	_, ok = e.(*UnsupportedEvaluator)
	assert.True(t, ok)
}

// --- toFloat64 / toBool edge case tests ---

func TestToFloat64_Types(t *testing.T) {
	f, ok := toFloat64(float64(3.14))
	assert.True(t, ok)
	assert.Equal(t, 3.14, f)

	f, ok = toFloat64(float32(2.5))
	assert.True(t, ok)
	assert.InDelta(t, 2.5, f, 0.001)

	f, ok = toFloat64(int(42))
	assert.True(t, ok)
	assert.Equal(t, float64(42), f)

	f, ok = toFloat64(int64(100))
	assert.True(t, ok)
	assert.Equal(t, float64(100), f)

	i := 7
	f, ok = toFloat64(&i)
	assert.True(t, ok)
	assert.Equal(t, float64(7), f)

	var nilIntPtr *int
	_, ok = toFloat64(nilIntPtr)
	assert.False(t, ok)

	_, ok = toFloat64("string")
	assert.False(t, ok)
}

func TestToBool_Types(t *testing.T) {
	b, ok := toBool(true)
	assert.True(t, ok)
	assert.True(t, b)

	b, ok = toBool(false)
	assert.True(t, ok)
	assert.False(t, b)

	boolVal := true
	b, ok = toBool(&boolVal)
	assert.True(t, ok)
	assert.True(t, b)

	var nilBoolPtr *bool
	_, ok = toBool(nilBoolPtr)
	assert.False(t, ok)

	_, ok = toBool("true")
	assert.False(t, ok)
}
