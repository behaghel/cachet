package id.cachet.wallet.domain.verification

import id.cachet.wallet.network.PredicateResultDTO
import id.cachet.wallet.network.VerificationSummaryDTO

sealed class LocalVerificationResult {

    data class Success(
        val badge: String,
        val freshness: String,
        val predicateResults: List<PredicateResultDTO>,
        val summary: VerificationSummaryDTO,
        val holderBound: Boolean,
        val revocationChecked: Boolean
    ) : LocalVerificationResult()

    data class VerificationFailed(
        val reason: String,
        val step: VerificationStep
    ) : LocalVerificationResult()

    data class Degraded(
        val result: Success,
        val warnings: List<String>
    ) : LocalVerificationResult()
}

enum class VerificationStep {
    PARSE,
    ISSUER_SIGNATURE,
    SD_ALG,
    DISCLOSURE_HASH,
    KB_JWT,
    NONCE,
    AUDIENCE,
    REVOKED,
    EXPIRED
}
