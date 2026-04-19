package id.cachet.wallet.domain.verification

import id.cachet.wallet.domain.model.PackBadge
import id.cachet.wallet.domain.model.PackDefinition
import id.cachet.wallet.domain.model.PackPredicate
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredicateEvaluatorTest {

    private fun makePack(vararg predicates: PackPredicate) = PackDefinition(
        id = "test.pack",
        version = "0.1.0",
        name = "Test Pack",
        purpose = "Testing",
        jurisdictions = listOf("GLOBAL"),
        badge = PackBadge(label = "Test Badge", ttl = "P90D", jurisdiction = "GLOBAL"),
        predicates = predicates.toList()
    )

    private fun makeClaims(issuer: String, vararg pairs: Pair<String, Any>) = VerifiedClaims(
        issuer = issuer,
        claims = pairs.associate { (k, v) ->
            k to when (v) {
                is Boolean -> JsonPrimitive(v)
                is Int -> JsonPrimitive(v)
                is Double -> JsonPrimitive(v)
                is String -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        }
    )

    @Test
    fun boolean_satisfied() {
        val pack = makePack(
            PackPredicate("p1", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "verified" to true))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals(1, result.predicateResults.size)
        assertEquals("satisfied", result.predicateResults[0].status)
        assertTrue(result.summary.cachetGranted)
    }

    @Test
    fun boolean_failed() {
        val pack = makePack(
            PackPredicate("p1", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "verified" to false))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("failed", result.predicateResults[0].status)
        assertFalse(result.summary.cachetGranted)
    }

    @Test
    fun greaterThanEqual_satisfied() {
        val pack = makePack(
            PackPredicate("age", "age", ">=", JsonPrimitive(18), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "age" to 21))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("satisfied", result.predicateResults[0].status)
    }

    @Test
    fun greaterThanEqual_exact() {
        val pack = makePack(
            PackPredicate("age", "age", ">=", JsonPrimitive(18), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "age" to 18))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("satisfied", result.predicateResults[0].status)
    }

    @Test
    fun greaterThanEqual_failed() {
        val pack = makePack(
            PackPredicate("age", "age", ">=", JsonPrimitive(18), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "age" to 16))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("failed", result.predicateResults[0].status)
    }

    @Test
    fun issuer_mismatch_noCredential() {
        val pack = makePack(
            PackPredicate("p1", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:other:something", "verified" to true))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("no_credential", result.predicateResults[0].status)
    }

    @Test
    fun empty_claims() {
        val pack = makePack(
            PackPredicate("p1", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
        )
        val result = PredicateEvaluator.evaluate(pack, emptyList())

        assertEquals("no_credential", result.predicateResults[0].status)
    }

    @Test
    fun missing_claim_noCredential() {
        val pack = makePack(
            PackPredicate("p1", "missing_claim", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "verified" to true))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("no_credential", result.predicateResults[0].status)
    }

    @Test
    fun required_and_optional_counts() {
        val pack = makePack(
            PackPredicate("p1", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt", required = true),
            PackPredicate("p2", "optional_claim", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt", required = false)
        )
        val claims = listOf(makeClaims("did:veriff:prod", "verified" to true))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals(1, result.summary.requiredTotal)
        assertEquals(1, result.summary.requiredSatisfied)
        assertEquals(1, result.summary.optionalTotal)
        assertEquals(0, result.summary.optionalSatisfied)
        assertTrue(result.summary.cachetGranted) // all required satisfied
    }

    @Test
    fun equalEqual_operator() {
        val pack = makePack(
            PackPredicate("p1", "nationality", "==", JsonPrimitive("EE"), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "nationality" to "EE"))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("satisfied", result.predicateResults[0].status)
    }

    @Test
    fun lessThan_operator() {
        val pack = makePack(
            PackPredicate("p1", "score", "<", JsonPrimitive(100), listOf("did:veriff:*"), "sd-jwt")
        )
        val claims = listOf(makeClaims("did:veriff:prod", "score" to 50))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals("satisfied", result.predicateResults[0].status)
    }

    @Test
    fun identity_basic_pack_full() {
        val pack = PackDefinition(
            id = "pack.identity.basic",
            version = "0.1.0",
            name = "Identity Verification",
            purpose = "Verify core identity attributes",
            jurisdictions = listOf("GLOBAL"),
            badge = PackBadge(label = "Identity Verified", ttl = "P180D", jurisdiction = "GLOBAL"),
            predicates = listOf(
                PackPredicate("age.ge.18", "age", ">=", JsonPrimitive(18), listOf("did:veriff:*"), "sd-jwt"),
                PackPredicate("identity.verified", "verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt"),
                PackPredicate("liveness.verified", "liveness_verified", "boolean", JsonPrimitive(true), listOf("did:veriff:*"), "sd-jwt")
            )
        )
        val claims = listOf(makeClaims(
            "did:veriff:production",
            "age" to 25,
            "verified" to true,
            "liveness_verified" to true
        ))
        val result = PredicateEvaluator.evaluate(pack, claims)

        assertEquals(3, result.predicateResults.size)
        assertTrue(result.predicateResults.all { it.status == "satisfied" })
        assertTrue(result.summary.cachetGranted)
        assertEquals("Identity Verified", pack.badge.label)
    }
}
