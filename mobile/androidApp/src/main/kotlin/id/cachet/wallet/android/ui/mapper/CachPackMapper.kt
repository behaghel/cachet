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
    }

    fun toCachetResult(pack: CachPackUi, allPassed: Boolean): CachetResult {
        val request = toVerificationRequest(pack)
        val name = if (allPassed) cachetName(pack.cachetType) else "Incomplete"
        return CachetResult(
            cachetName = name,
            allPassed = allPassed,
            passedCount = if (allPassed) request.predicates.size else 0,
            totalCount = request.predicates.size,
            predicates = request.predicates.map { pred ->
                PredicateResult(
                    label = pred.claim,
                    passed = allPassed,
                    failReason = if (!allPassed) "Credential not available" else null,
                    privacyNote = pred.privacyNote,
                    disclosureType = pred.disclosureType
                )
            },
            validityLabel = if (allPassed) "${request.retentionDays} days" else null,
            cachetType = pack.cachetType
        )
    }

    private fun cachetName(type: CachetType): String = when (type) {
        CachetType.CHILDCARE -> "Childcare Ready"
        CachetType.SELLER -> "Trusted Seller"
        CachetType.AGE -> "Age Verified"
        CachetType.IDENTITY -> "Identity Verified"
    }
}
