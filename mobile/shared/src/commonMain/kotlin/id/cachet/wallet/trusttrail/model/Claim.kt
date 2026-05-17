package id.cachet.wallet.trusttrail.model

/**
 * A single piece of evidence extracted from an email.
 *
 * @property type Semantic claim type, e.g., "booking_confirmation", "sale_amount"
 * @property confidence Extraction confidence 0.0–1.0; higher = more certain the regex matched correctly
 * @property fields Captured named groups, e.g., {"amount": "45.00", "buyer": "alice"}
 * @property source Where the claim was found: "subject", "body_text", or "body_html"
 */
data class Claim(
    val type: String,
    val confidence: Double,
    val fields: Map<String, String>,
    val source: String,
)
