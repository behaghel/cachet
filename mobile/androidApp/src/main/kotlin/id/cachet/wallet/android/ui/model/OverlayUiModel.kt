package id.cachet.wallet.android.ui.model

import id.cachet.wallet.android.ui.components.CachetType

data class QrShareState(
    val question: String,
    val predicates: List<String>,
    val expiresLabel: String = "Request expires in 4:58",
    val qrPayload: String = ""
)

enum class DisclosureType { PREDICATE, RAW_VALUE }

data class RequestPredicate(
    val claim: String,
    val privacyNote: String,
    val disclosureType: DisclosureType = DisclosureType.PREDICATE
)

data class VerificationRequest(
    val question: String,
    val predicates: List<RequestPredicate>,
    val retentionDays: Int = 90,
    val loggedInTransparencyLog: Boolean = true
)

data class PredicateResult(
    val label: String,
    val passed: Boolean,
    val failReason: String? = null
)

data class CachetResult(
    val cachetName: String,
    val allPassed: Boolean,
    val passedCount: Int,
    val totalCount: Int,
    val predicates: List<PredicateResult>,
    val validityLabel: String? = null,
    val cachetType: CachetType = CachetType.IDENTITY
)
