package eval

import (
	"fmt"

	"github.com/cachet-id/cachet/generated/go/models"
)

// VerifiedSDJWTEvaluator evaluates predicates against cryptographically verified SD-JWT claims.
// Unlike SDJWTEvaluator, the claims have already been verified (issuer sig + disclosure hashes).
type VerifiedSDJWTEvaluator struct{}

// EvaluateVerified evaluates a predicate against verified claims from VerifySDJWT.
func (e *VerifiedSDJWTEvaluator) EvaluateVerified(pred models.PredicateDefinition, verifiedClaims []*VerifiedClaims) models.PredicateResult {
	for _, vc := range verifiedClaims {
		if !matchesIssuer(vc.Issuer, pred.IssuersAccepted) {
			continue
		}

		val, ok := vc.Claims[pred.Claim]
		if !ok {
			continue
		}

		satisfied, reason := evaluateOperator(pred.Operator, val, pred.Value)
		if satisfied {
			return models.PredicateResult{
				PredicateId: pred.Id,
				Status:      models.Satisfied,
			}
		}
		return models.PredicateResult{
			PredicateId: pred.Id,
			Status:      models.Failed,
			Reason:      &reason,
		}
	}

	reason := "no credential from an accepted issuer"
	return models.PredicateResult{
		PredicateId: pred.Id,
		Status:      models.NoCredential,
		Reason:      &reason,
	}
}

// SDJWTEvaluator evaluates predicates that require SD-JWT proof (legacy, no crypto verification).
type SDJWTEvaluator struct{}

func (e *SDJWTEvaluator) Evaluate(pred models.PredicateDefinition, credentials []models.VerifiableCredential) models.PredicateResult {
	// Find a credential from an accepted issuer
	for _, cred := range credentials {
		if !matchesIssuer(cred.Issuer, pred.IssuersAccepted) {
			continue
		}

		// Extract claim value
		val, ok := ResolveClaim(cred.CredentialSubject, pred.Claim)
		if !ok {
			continue
		}

		// Evaluate operator
		satisfied, reason := evaluateOperator(pred.Operator, val, pred.Value)
		if satisfied {
			return models.PredicateResult{
				PredicateId: pred.Id,
				Status:      models.Satisfied,
			}
		}
		return models.PredicateResult{
			PredicateId: pred.Id,
			Status:      models.Failed,
			Reason:      &reason,
		}
	}

	reason := "no credential from an accepted issuer"
	return models.PredicateResult{
		PredicateId: pred.Id,
		Status:      models.NoCredential,
		Reason:      &reason,
	}
}

func evaluateOperator(op models.PredicateDefinitionOperator, actual interface{}, expected interface{}) (bool, string) {
	switch op {
	case models.Boolean:
		actualBool, ok := actual.(bool)
		if !ok {
			return false, fmt.Sprintf("expected boolean, got %T", actual)
		}
		expectedBool, ok := toBool(expected)
		if !ok {
			return false, fmt.Sprintf("expected boolean value, got %v", expected)
		}
		if actualBool == expectedBool {
			return true, ""
		}
		return false, fmt.Sprintf("expected %v, got %v", expectedBool, actualBool)

	case models.GreaterThanEqual:
		actualNum, ok := toFloat64(actual)
		if !ok {
			return false, fmt.Sprintf("expected number, got %T", actual)
		}
		expectedNum, ok := toFloat64(expected)
		if !ok {
			return false, fmt.Sprintf("expected number value, got %v", expected)
		}
		if actualNum >= expectedNum {
			return true, ""
		}
		return false, fmt.Sprintf("expected >= %v, got %v", expectedNum, actualNum)

	case models.GreaterThan:
		actualNum, ok := toFloat64(actual)
		if !ok {
			return false, fmt.Sprintf("expected number, got %T", actual)
		}
		expectedNum, ok := toFloat64(expected)
		if !ok {
			return false, fmt.Sprintf("expected number value, got %v", expected)
		}
		if actualNum > expectedNum {
			return true, ""
		}
		return false, fmt.Sprintf("expected > %v, got %v", expectedNum, actualNum)

	case models.LessThan:
		actualNum, ok := toFloat64(actual)
		if !ok {
			return false, fmt.Sprintf("expected number, got %T", actual)
		}
		expectedNum, ok := toFloat64(expected)
		if !ok {
			return false, fmt.Sprintf("expected number value, got %v", expected)
		}
		if actualNum < expectedNum {
			return true, ""
		}
		return false, fmt.Sprintf("expected < %v, got %v", expectedNum, actualNum)

	case models.LessThanEqual:
		actualNum, ok := toFloat64(actual)
		if !ok {
			return false, fmt.Sprintf("expected number, got %T", actual)
		}
		expectedNum, ok := toFloat64(expected)
		if !ok {
			return false, fmt.Sprintf("expected number value, got %v", expected)
		}
		if actualNum <= expectedNum {
			return true, ""
		}
		return false, fmt.Sprintf("expected <= %v, got %v", expectedNum, actualNum)

	case models.EqualEqual:
		if fmt.Sprintf("%v", actual) == fmt.Sprintf("%v", expected) {
			return true, ""
		}
		return false, fmt.Sprintf("expected == %v, got %v", expected, actual)

	default:
		return false, fmt.Sprintf("unsupported operator: %s", op)
	}
}

func toFloat64(v interface{}) (float64, bool) {
	switch n := v.(type) {
	case float64:
		return n, true
	case float32:
		return float64(n), true
	case int:
		return float64(n), true
	case int64:
		return float64(n), true
	case *int:
		if n == nil {
			return 0, false
		}
		return float64(*n), true
	default:
		return 0, false
	}
}

func toBool(v interface{}) (bool, bool) {
	switch b := v.(type) {
	case bool:
		return b, true
	case *bool:
		if b == nil {
			return false, false
		}
		return *b, true
	default:
		return false, false
	}
}
