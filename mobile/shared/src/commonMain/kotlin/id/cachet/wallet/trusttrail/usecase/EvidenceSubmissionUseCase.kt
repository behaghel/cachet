package id.cachet.wallet.trusttrail.usecase

import id.cachet.wallet.trusttrail.model.EvidenceBundle

/**
 * Submits an evidence bundle to the issuance gateway.
 * Requires foundational identity (Veriff) before submission is allowed.
 *
 * Dependencies are injected as lambdas to avoid coupling to the wallet domain.
 */
class EvidenceSubmissionUseCase(
    private val hasFoundationalIdentity: suspend () -> Boolean,
    private val submitBundle: suspend (EvidenceBundle) -> Boolean,
) {

    /**
     * Submit an evidence bundle for cachet issuance.
     *
     * Returns a result indicating success or the reason for failure.
     */
    suspend fun submit(bundle: EvidenceBundle): SubmissionResult {
        if (!hasFoundationalIdentity()) {
            return SubmissionResult(
                success = false,
                reason = "foundational_identity_required",
            )
        }

        val sent = submitBundle(bundle)
        return if (sent) {
            SubmissionResult(success = true, reason = null)
        } else {
            SubmissionResult(success = false, reason = "submission_failed")
        }
    }
}

data class SubmissionResult(
    val success: Boolean,
    val reason: String?,
)
