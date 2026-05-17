package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.*

/**
 * Maps CachPackUi to domain-level VerificationRequest and CachetResult.
 * Each cachet type has its own predicate set and privacy notes.
 */
object CachPackMapper {

    fun toVerificationRequest(pack: CachPackUi): VerificationRequest = when (pack.cachetType) {
        CachetType.CHILDCARE -> VerificationRequest(
            question = "Are you safe for childcare?",
            predicates = listOf(
                RequestPredicate("You are 18 or older", "Your exact age will NOT be shared", DisclosureType.PREDICATE),
                RequestPredicate("Your identity is verified", "Your name will NOT be shared", DisclosureType.PREDICATE),
                RequestPredicate("No criminal record", "Only a clear/not-clear result", DisclosureType.PREDICATE),
                RequestPredicate("2+ verified references", "Referee names will NOT be shared", DisclosureType.PREDICATE)
            ),
            retentionDays = 90,
            loggedInTransparencyLog = true,
            cachetType = CachetType.CHILDCARE
        )
        CachetType.SELLER -> VerificationRequest(
            question = "Are you a trusted seller?",
            predicates = listOf(
                RequestPredicate("Your identity is verified", "Your name will NOT be shared", DisclosureType.PREDICATE),
                RequestPredicate("Platform name", "Shared as-is from your credential", DisclosureType.RAW_VALUE),
                RequestPredicate("Fulfilment rate above 95%", "Only a pass/fail result", DisclosureType.PREDICATE),
                RequestPredicate("Low chargeback rate", "Only a pass/fail result", DisclosureType.PREDICATE)
            ),
            retentionDays = 90,
            loggedInTransparencyLog = true,
            cachetType = CachetType.SELLER
        )
        CachetType.AGE -> VerificationRequest(
            question = "Are you old enough?",
            predicates = listOf(
                RequestPredicate("You are 18 or older", "Your exact age will NOT be shared", DisclosureType.PREDICATE)
            ),
            retentionDays = 30,
            loggedInTransparencyLog = true,
            cachetType = CachetType.AGE
        )
        CachetType.IDENTITY -> VerificationRequest(
            question = "Is your identity verified?",
            predicates = listOf(
                RequestPredicate("Full name", "Shared as-is from your credential", DisclosureType.RAW_VALUE),
                RequestPredicate("Liveness check passed", "Only a pass/fail result", DisclosureType.PREDICATE)
            ),
            retentionDays = 90,
            loggedInTransparencyLog = true,
            cachetType = CachetType.IDENTITY
        )
        CachetType.TRUSTED_HOST -> VerificationRequest(
            question = "Are you a trusted host?",
            predicates = listOf(
                RequestPredicate("Verified hosting track record", "Based on confirmed exchanges, not reviews", DisclosureType.PREDICATE),
                RequestPredicate("Identity verified", "Linked to an identity cachet", DisclosureType.PREDICATE)
            ),
            retentionDays = 90,
            loggedInTransparencyLog = true,
            cachetType = CachetType.TRUSTED_HOST
        )
    }

    fun toCachetResult(pack: CachPackUi, allPassed: Boolean): CachetResult {
        val request = toVerificationRequest(pack)
        val name = if (allPassed) cachetName(pack.cachetType) else "Incomplete"
        val failReasons = if (!allPassed) failReasonsFor(pack.cachetType) else emptyMap()
        val predicates = request.predicates.mapIndexed { idx, pred ->
            val failed = failReasons.containsKey(idx)
            PredicateResult(
                label = pred.claim,
                passed = !failed,
                failReason = failReasons[idx],
                privacyNote = pred.privacyNote,
                disclosureType = pred.disclosureType
            )
        }
        val passedCount = predicates.count { it.passed }
        return CachetResult(
            cachetName = name,
            allPassed = allPassed,
            passedCount = passedCount,
            totalCount = request.predicates.size,
            predicates = predicates,
            validityLabel = if (allPassed) "${request.retentionDays} days" else null,
            cachetType = pack.cachetType
        )
    }

    /** Per-predicate failure reasons by index. Only the listed indices fail. */
    private fun failReasonsFor(type: CachetType): Map<Int, String> = when (type) {
        CachetType.CHILDCARE -> mapOf(
            2 to "Credential not available",
            3 to "Only 1 reference on file"
        )
        CachetType.SELLER -> mapOf(
            2 to "Fulfilment rate below threshold",
            3 to "Chargeback data unavailable"
        )
        CachetType.AGE -> mapOf(
            0 to "Age credential expired"
        )
        CachetType.IDENTITY -> mapOf(
            1 to "Liveness check not completed"
        )
        CachetType.TRUSTED_HOST -> mapOf(
            0 to "Insufficient hosting evidence"
        )
    }

    private fun cachetName(type: CachetType): String = when (type) {
        CachetType.CHILDCARE -> "Childcare Ready"
        CachetType.SELLER -> "Trusted Seller"
        CachetType.AGE -> "Age Verified"
        CachetType.IDENTITY -> "Identity Verified"
        CachetType.TRUSTED_HOST -> "Trusted Host"
    }

    /** User-facing pack name for the liveness explanation screen. */
    fun cachetDisplayName(type: CachetType): String = when (type) {
        CachetType.CHILDCARE -> "Childcare Readiness"
        CachetType.SELLER -> "Safe Seller"
        CachetType.AGE -> "Age Verification"
        CachetType.IDENTITY -> "Identity Verification"
        CachetType.TRUSTED_HOST -> "Trusted Host"
    }
}
