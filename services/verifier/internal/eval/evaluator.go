package eval

import "github.com/cachet-id/cachet/generated/go/models"

// PredicateEvaluator evaluates a single predicate against credentials.
type PredicateEvaluator interface {
	Evaluate(predicate models.PredicateDefinition, credentials []models.VerifiableCredential) models.PredicateResult
}

// Evaluate evaluates all predicates in a pack against the provided credentials.
func Evaluate(pack models.PackDefinition, credentials []models.VerifiableCredential) ([]models.PredicateResult, models.VerificationSummary) {
	var results []models.PredicateResult
	requiredTotal, requiredSatisfied := 0, 0
	optionalTotal, optionalSatisfied := 0, 0

	for _, pred := range pack.Predicates {
		evaluator := evaluatorFor(pred.ProofType)
		result := evaluator.Evaluate(pred, credentials)
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
		BadgeGranted:      requiredSatisfied == requiredTotal,
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
