package eval

import "github.com/cachet-id/cachet/generated/go/models"

// PredicateEvaluator evaluates a single predicate against credentials.
type PredicateEvaluator interface {
	Evaluate(predicate models.PredicateDefinition, credentials []models.VerifiableCredential) models.PredicateResult
}

// Evaluate evaluates all predicates in a pack against the provided credentials.
// Supports both legacy JSON credentials and verified SD-JWT claims.
func Evaluate(pack models.PackDefinition, credentials []models.VerifiableCredential) ([]models.PredicateResult, models.VerificationSummary) {
	return evaluateWithClaims(pack, credentials, nil)
}

// EvaluateWithVerifiedClaims evaluates predicates using cryptographically verified SD-JWT claims.
// The verifiedClaims are produced by VerifySDJWT and contain only claims whose
// disclosure hashes and issuer signatures have been checked.
func EvaluateWithVerifiedClaims(pack models.PackDefinition, verifiedClaims []*VerifiedClaims) ([]models.PredicateResult, models.VerificationSummary) {
	return evaluateWithClaims(pack, nil, verifiedClaims)
}

func evaluateWithClaims(pack models.PackDefinition, credentials []models.VerifiableCredential, verifiedClaims []*VerifiedClaims) ([]models.PredicateResult, models.VerificationSummary) {
	var results []models.PredicateResult
	requiredTotal, requiredSatisfied := 0, 0
	optionalTotal, optionalSatisfied := 0, 0

	for _, pred := range pack.Predicates {
		var result models.PredicateResult

		if len(verifiedClaims) > 0 {
			evaluator := &VerifiedSDJWTEvaluator{}
			result = evaluator.EvaluateVerified(pred, verifiedClaims)
		} else {
			evaluator := evaluatorFor(pred.ProofType)
			result = evaluator.Evaluate(pred, credentials)
		}
		results = append(results, result)

		required := pred.Required == nil || *pred.Required // default true
		if required {
			requiredTotal++
			if result.Status == models.Satisfied {
				requiredSatisfied++
			}
		} else {
			optionalTotal++
			if result.Status == models.Satisfied {
				optionalSatisfied++
			}
		}
	}

	summary := models.VerificationSummary{
		RequiredSatisfied: requiredSatisfied,
		RequiredTotal:     requiredTotal,
		OptionalSatisfied: &optionalSatisfied,
		OptionalTotal:     &optionalTotal,
		CachetGranted:     requiredSatisfied == requiredTotal,
	}

	return results, summary
}

func evaluatorFor(proofType models.PredicateDefinitionProofType) PredicateEvaluator {
	switch proofType {
	case models.SdJwt:
		return &SDJWTEvaluator{}
	default:
		return &UnsupportedEvaluator{}
	}
}
