package id.cachet.wallet.trusttrail.strength

/**
 * Controls how evidence strength decreases over time.
 *
 * @property windowMonths After this many months, evidence strength reaches zero (default: 12)
 * @property decayFunction Currently only "linear" is supported
 */
data class DecayConfig(
    val windowMonths: Int = 12,
    val decayFunction: String = "linear",
)
