package eval

import (
	"fmt"

	"github.com/cachet-id/cachet/generated/go/models"
)

// UnsupportedEvaluator handles proof types that are not yet implemented (e.g., BBS+, ZK-SNARK).
type UnsupportedEvaluator struct{}

func (e *UnsupportedEvaluator) Evaluate(pred models.PredicateDefinition, credentials []models.VerifiableCredential) models.PredicateResult {
	reason := fmt.Sprintf("proof type %s not yet supported", pred.ProofType)
	return models.PredicateResult{
		PredicateId: pred.Id,
		Status:      models.NotEvaluable,
		Reason:      &reason,
	}
}
