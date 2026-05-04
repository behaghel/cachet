package id.cachet.wallet.trusttrail.model

import kotlinx.datetime.Instant

/**
 * Lightweight email header — fetched without reading the body.
 * Used during platform discovery (phase 1 of two-phase pull).
 *
 * @property fromDomain Sending domain extracted from the From header
 * @property subject Email subject line
 * @property date Email Date header
 * @property messageId Provider-specific message identifier for later full fetch
 */
data class EmailHeader(
    val fromDomain: String,
    val subject: String,
    val date: Instant,
    val messageId: String,
)
