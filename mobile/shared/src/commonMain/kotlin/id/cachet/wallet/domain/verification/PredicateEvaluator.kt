package id.cachet.wallet.domain.verification

import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.model.PackPredicate
import id.cachet.wallet.network.PredicateResultDTO
import id.cachet.wallet.network.VerificationSummaryDTO
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Evaluates pack predicates against cryptographically verified claims.
 *
 * Port of Go `services/verifier/internal/eval/evaluator.go` + `sdjwt.go`.
 */
object PredicateEvaluator {

    data class EvaluationResult(
        val predicateResults: List<PredicateResultDTO>,
        val summary: VerificationSummaryDTO
    )

    fun evaluate(pack: PackDefinition, verifiedClaims: List<VerifiedClaims>): EvaluationResult {
        val results = mutableListOf<PredicateResultDTO>()
        var requiredTotal = 0
        var requiredSatisfied = 0
        var optionalTotal = 0
        var optionalSatisfied = 0

        for (pred in pack.predicates) {
            val result = evaluatePredicate(pred, verifiedClaims)
            results.add(result)

            if (pred.required) {
                requiredTotal++
                if (result.status == "satisfied") requiredSatisfied++
            } else {
                optionalTotal++
                if (result.status == "satisfied") optionalSatisfied++
            }
        }

        return EvaluationResult(
            predicateResults = results,
            summary = VerificationSummaryDTO(
                requiredSatisfied = requiredSatisfied,
                requiredTotal = requiredTotal,
                optionalSatisfied = optionalSatisfied,
                optionalTotal = optionalTotal,
                cachetGranted = requiredSatisfied == requiredTotal
            )
        )
    }

    private fun evaluatePredicate(pred: PackPredicate, verifiedClaims: List<VerifiedClaims>): PredicateResultDTO {
        for (vc in verifiedClaims) {
            if (!IssuerMatcher.matches(vc.issuer, pred.issuersAccepted)) continue

            val actual = vc.claims[pred.claim] ?: continue

            val (satisfied, reason) = evaluateOperator(pred.operator, actual, pred.value)
            return if (satisfied) {
                PredicateResultDTO(predicateId = pred.id, status = "satisfied")
            } else {
                PredicateResultDTO(predicateId = pred.id, status = "failed", reason = reason)
            }
        }
        return PredicateResultDTO(
            predicateId = pred.id,
            status = "no_credential",
            reason = "no credential from an accepted issuer"
        )
    }

    private fun evaluateOperator(op: String, actual: JsonElement, expected: JsonPrimitive): Pair<Boolean, String?> {
        return when (op) {
            "boolean" -> {
                val actualBool = (actual as? JsonPrimitive)?.booleanOrNull
                    ?: return false to "expected boolean, got $actual"
                val expectedBool = expected.booleanOrNull
                    ?: return false to "expected boolean value, got $expected"
                if (actualBool == expectedBool) true to null
                else false to "expected $expectedBool, got $actualBool"
            }
            ">=" -> compareNumbers(actual, expected) { a, e -> a >= e }
            ">" -> compareNumbers(actual, expected) { a, e -> a > e }
            "<" -> compareNumbers(actual, expected) { a, e -> a < e }
            "<=" -> compareNumbers(actual, expected) { a, e -> a <= e }
            "==" -> {
                if (actual.toString() == expected.toString()) true to null
                else false to "expected == $expected, got $actual"
            }
            else -> false to "unsupported operator: $op"
        }
    }

    private fun compareNumbers(
        actual: JsonElement,
        expected: JsonPrimitive,
        compare: (Double, Double) -> Boolean
    ): Pair<Boolean, String?> {
        val actualNum = (actual as? JsonPrimitive)?.doubleOrNull
            ?: return false to "expected number, got $actual"
        val expectedNum = expected.doubleOrNull
            ?: return false to "expected number value, got $expected"
        return if (compare(actualNum, expectedNum)) true to null
        else false to "expected $expected, got $actualNum"
    }
}
