package id.cachet.wallet.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
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
        
        // Verification level indicator
        indicators.add(
            QualityIndicator(
                icon = verificationLevel.emoji,
                title = verificationLevel.displayName,
                subtitle = "Confidence: ${(overallConfidence * 100).toInt()}%",
                score = overallConfidence
            )
        )
        
        // Privacy support indicator
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
        
        // Freshness indicator
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
        
        // Risk assessment (if available)
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
 * Extension functions for VerifiableCredential to extract quality information
 */
fun VerifiableCredential.extractQuality(): CredentialQuality? {
    try {
        // Extract verification level
        val verificationLevel = credentialSubject["verificationLevel"]?.jsonPrimitive?.contentOrNull
            ?.let { VerificationLevel.fromString(it) } ?: VerificationLevel.BASIC
        
        // Extract verification metrics
        val metricsObj = credentialSubject["verificationMetrics"]?.jsonObject
        val overallConfidence = metricsObj?.get("overallConfidence")?.jsonPrimitive?.doubleOrNull ?: 0.85
        val riskScore = metricsObj?.get("riskScore")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val livenessScore = metricsObj?.get("livenessScore")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val documentAuthenticity = metricsObj?.get("documentAuthenticity")?.jsonPrimitive?.doubleOrNull ?: 0.0
        
        // Calculate freshness
        val issuanceInstant = getIssuanceInstant() ?: kotlinx.datetime.Clock.System.now()
        val freshness = kotlinx.datetime.Clock.System.now() - issuanceInstant
        
        // Determine privacy support (for now, assume all credentials support selective disclosure)
        val privacySupport = listOf("selective_disclosure", "predicate_proofs")
        
        // Calculate trust score based on multiple factors
        val trustScore = (overallConfidence * 0.4) + 
                        ((1.0 - riskScore) * 0.3) + 
                        (livenessScore * 0.15) + 
                        (documentAuthenticity * 0.15)
        
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
        // Return basic quality if parsing fails
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