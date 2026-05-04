package id.cachet.wallet.trusttrail.model

import kotlinx.datetime.Instant

/**
 * Structured evidence extracted from a single email.
 *
 * @property platform Detected platform name (e.g., "vinted", "care.com"), empty if unknown
 * @property fromDomain Sending domain extracted from the From header
 * @property subject Original subject line
 * @property receivedDate Email Date header
 * @property claims Extracted structured claims
 * @property rejected True if the email was rejected as evidence (e.g., forwarded)
 * @property rejectionReason Why rejected (e.g., "forwarded_email", "broken_signature")
 */
data class EmailEvidence(
    val platform: String,
    val fromDomain: String,
    val subject: String,
    val receivedDate: Instant,
    val claims: List<Claim>,
    val rejected: Boolean = false,
    val rejectionReason: String? = null,
    val trustLevel: TrustLevel? = null,
    val dkimDomain: String? = null,
)
