package id.cachet.wallet.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Represents the quality level of a verifiable credential verification
 */
@Serializable
enum class VerificationLevel(val displayName: String, val emoji: String) {
    BASIC("Basic Verification", "✅"),
    STANDARD("Standard Verification", "🔵"),
    PREMIUM("Premium Verification", "🟡"),
    GOLD("Gold Verification", "🏆");

    companion object {
        fun fromString(value: String?): VerificationLevel {
            return when (value?.lowercase()) {
                "basic" -> BASIC
                "standard" -> STANDARD
                "premium" -> PREMIUM
                "gold" -> GOLD
                else -> BASIC
            }
        }
    }
}

/**
 * Represents the overall quality assessment of a credential
 */
@Serializable
data class CredentialQuality(
    val verificationLevel: VerificationLevel,
    val overallConfidence: Double,
    val freshness: Duration,
    val privacySupport: List<String>,
    val trustScore: Double,
    val riskScore: Double = 0.0,
    val livenessScore: Double = 0.0,
    val documentAuthenticity: Double = 0.0
) {
    /**
     * Gets a human-readable quality summary
     */
    fun getQualitySummary(): String {
        val freshnessText = when {
            freshness <= 1.days -> "Fresh (${freshness.inWholeDays} day)"
            freshness <= 7.days -> "Fresh (${freshness.inWholeDays} days)"
            freshness <= 30.days -> "Recent (${freshness.inWholeDays} days)"
            else -> "Aged (${freshness.inWholeDays} days)"
        }

        return "${verificationLevel.emoji} ${verificationLevel.displayName} • 🔒 Privacy Ready • ⏰ $freshnessText"
    }

    /**
     * Gets quality indicators as a list of display items
     */
    fun getQualityIndicators(): List<QualityIndicator> {
        val indicators = mutableListOf<QualityIndicator>()

        indicators.add(
            QualityIndicator(
                icon = verificationLevel.emoji,
                title = verificationLevel.displayName,
                subtitle = "Confidence: ${(overallConfidence * 100).toInt()}%",
                score = overallConfidence
            )
        )

        if (privacySupport.isNotEmpty()) {
            indicators.add(
                QualityIndicator(
                    icon = "🔒",
                    title = "Privacy Ready",
                    subtitle = "Selective disclosure supported",
                    score = 1.0
                )
            )
        }

        val freshnessIcon = when {
            freshness <= 7.days -> "🟢"
            freshness <= 30.days -> "🟡"
            else -> "🟠"
        }
        indicators.add(
            QualityIndicator(
                icon = freshnessIcon,
                title = "Freshness",
                subtitle = "${freshness.inWholeDays} days old",
                score = maxOf(0.0, 1.0 - (freshness.inWholeDays / 90.0))
            )
        )

        if (riskScore > 0) {
            val riskIcon = when {
                riskScore < 0.1 -> "🟢"
                riskScore < 0.3 -> "🟡"
                else -> "🔴"
            }
            indicators.add(
                QualityIndicator(
                    icon = riskIcon,
                    title = "Risk Assessment",
                    subtitle = "${((1.0 - riskScore) * 100).toInt()}% safe",
                    score = 1.0 - riskScore
                )
            )
        }

        return indicators
    }
}

/**
 * Individual quality indicator for UI display
 */
@Serializable
data class QualityIndicator(
    val icon: String,
    val title: String,
    val subtitle: String,
    val score: Double // 0.0 to 1.0
)

/**
 * Extract quality information from credential using typed fields.
 * The backend determines quality tiers at issuance; we display them.
 */
fun VerifiableCredential.extractQuality(): CredentialQuality? {
    try {
        val verificationLevel = VerificationLevel.fromString(credentialSubject.verificationLevel)

        val metrics = credentialSubject.verificationMetrics
        val overallConfidence = metrics?.overallConfidence ?: 0.85
        val riskScore = metrics?.riskScore ?: 0.0
        val livenessScore = metrics?.livenessScore ?: 0.0
        val documentAuthenticity = metrics?.documentAuthenticity ?: 0.0

        val issuanceInstant = getIssuanceInstant() ?: kotlinx.datetime.Clock.System.now()
        val freshness = kotlinx.datetime.Clock.System.now() - issuanceInstant

        val privacySupport = listOf("selective_disclosure", "predicate_proofs")
        val trustScore = overallConfidence

        return CredentialQuality(
            verificationLevel = verificationLevel,
            overallConfidence = overallConfidence,
            freshness = freshness,
            privacySupport = privacySupport,
            trustScore = trustScore,
            riskScore = riskScore,
            livenessScore = livenessScore,
            documentAuthenticity = documentAuthenticity
        )
    } catch (e: Exception) {
        val issuanceInstant = getIssuanceInstant() ?: kotlinx.datetime.Clock.System.now()
        val freshness = kotlinx.datetime.Clock.System.now() - issuanceInstant

        return CredentialQuality(
            verificationLevel = VerificationLevel.BASIC,
            overallConfidence = 0.75,
            freshness = freshness,
            privacySupport = emptyList(),
            trustScore = 0.75
        )
    }
}

/**
 * Get a short quality badge text for UI
 */
fun VerifiableCredential.getQualityBadge(): String {
    val quality = extractQuality() ?: return "Basic"
    return "${quality.verificationLevel.emoji} ${quality.verificationLevel.displayName}"
}

/**
 * Check if the credential meets minimum quality thresholds
 */
fun VerifiableCredential.meetsQualityThreshold(): Boolean {
    val quality = extractQuality() ?: return false
    return quality.overallConfidence >= 0.8 &&
           quality.riskScore <= 0.3 &&
           quality.freshness <= 90.days
}
