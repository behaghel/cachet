package id.cachet.wallet.trusttrail.dkim

import id.cachet.wallet.trusttrail.model.TrustLevel

/**
 * Email provider type — determines the DKIM verification strategy.
 */
enum class ProviderType {
    /** Gmail preserves DKIM body hashes → on-device cryptographic verification possible. */
    GMAIL,
    /** Outlook breaks DKIM body hashes → must use MTA attestation only. */
    OUTLOOK,
}

/**
 * Result of on-device DKIM signature verification.
 */
enum class DkimResult {
    /** DKIM signature verified successfully against DNS public key. */
    PASS,
    /** DKIM signature verification failed (mismatch, key rotated, DNS error). */
    FAIL,
    /** DKIM verification was not performed (e.g., Outlook provider, no raw MIME). */
    NOT_CHECKED,
}

/**
 * Outcome of trust evaluation for a single email.
 */
data class TrustEvaluation(
    /** The determined trust level, or null if the email should be rejected. */
    val trustLevel: TrustLevel?,
    /** Rejection reason if trustLevel is null. */
    val rejectionReason: String?,
)

/**
 * Evaluates the trust level of an email based on provider type,
 * DKIM verification result, and MTA Authentication-Results headers.
 *
 * Decision tree:
 * - Gmail + DKIM pass → cryptographic
 * - Gmail + DKIM fail + AR dkim=pass → mta_attested (key may have rotated)
 * - Gmail + DKIM fail + no AR pass → rejected (broken_signature)
 * - Gmail + not checked + AR dkim=pass → mta_attested
 * - Gmail + not checked + no AR pass → rejected
 * - Outlook + AR dkim=pass → mta_attested (DKIM never attempted)
 * - Outlook + no AR pass → rejected
 */
object TrustEvaluator {

    fun evaluate(
        providerType: ProviderType,
        dkimResult: DkimResult,
        authenticationResultsHeaders: List<String>,
    ): TrustEvaluation {
        val mtaAttested = AuthenticationResultsParser.anyDkimPass(authenticationResultsHeaders)

        return when (providerType) {
            ProviderType.GMAIL -> evaluateGmail(dkimResult, mtaAttested)
            ProviderType.OUTLOOK -> evaluateOutlook(mtaAttested)
        }
    }

    private fun evaluateGmail(dkimResult: DkimResult, mtaAttested: Boolean): TrustEvaluation {
        return when (dkimResult) {
            DkimResult.PASS -> TrustEvaluation(TrustLevel.CRYPTOGRAPHIC, null)
            DkimResult.FAIL -> {
                if (mtaAttested) {
                    TrustEvaluation(TrustLevel.MTA_ATTESTED, null)
                } else {
                    TrustEvaluation(null, "broken_signature")
                }
            }
            DkimResult.NOT_CHECKED -> {
                if (mtaAttested) {
                    TrustEvaluation(TrustLevel.MTA_ATTESTED, null)
                } else {
                    TrustEvaluation(null, "broken_signature")
                }
            }
        }
    }

    private fun evaluateOutlook(mtaAttested: Boolean): TrustEvaluation {
        // Outlook breaks DKIM body hashes — never attempt cryptographic verification
        return if (mtaAttested) {
            TrustEvaluation(TrustLevel.MTA_ATTESTED, null)
        } else {
            TrustEvaluation(null, "broken_signature")
        }
    }
}
