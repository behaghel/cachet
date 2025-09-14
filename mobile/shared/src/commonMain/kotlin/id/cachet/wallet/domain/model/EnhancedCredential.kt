package id.cachet.wallet.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Phase A: Enhanced Veriff Integration - Privacy Vault Architecture
 * 
 * The strategic model: users share EVERYTHING with the app once
 * so they can share minimal predicates with everyone else.
 */

/**
 * Enhanced credential with privacy vault - the core of our "share once, prove many" strategy
 */
@Serializable
data class EnhancedCredential(
    val id: String,
    val publicCredential: VerifiableCredential, // Standard W3C VC for presentations
    val sensitiveVault: EncryptedSensitiveVault, // Never leaves device, never shared
    val qualityProfile: CredentialQualityProfile, // Rich quality metrics
    val privacyBudget: PrivacyBudget, // Track what's been shared to whom
    val derivedPredicates: List<AvailablePredicate>, // Computed locally from vault
    val createdAt: Instant,
    val lastUpdated: Instant
)

/**
 * Encrypted sensitive data vault - users trust us with everything
 */
@Serializable
data class EncryptedSensitiveVault(
    // Identity Layer - Full PII (encrypted)
    val fullIdentity: EncryptedData, // Full name, DOB, addresses, etc.
    val biometricTemplates: EncryptedData, // Face templates, fingerprints
    val documentImages: EncryptedData, // Full document photos
    val identityHistory: EncryptedData, // Previous addresses, name changes
    
    // Verification Layer - Rich Veriff data  
    val verificationDetails: EncryptedData, // Full Veriff response with scores
    val riskAssessment: EncryptedData, // Fraud signals, risk indicators
    val deviceFingerprint: EncryptedData, // Device trust signals
    val sessionMetrics: EncryptedData, // Verification session behavior
    
    // Financial Layer - Future expansion
    val financialProfile: EncryptedData?, // Credit scores, income verification
    val assetVerification: EncryptedData?, // Property, investments
    val paymentHistory: EncryptedData?, // Transaction patterns
    
    // Social Layer - Future expansion  
    val professionalReferences: EncryptedData?, // LinkedIn, employment verification
    val socialProofs: EncryptedData?, // Social media verification
    val communityEndorsements: EncryptedData?, // Local community trust
    
    // Behavioral Layer - Future expansion
    val appBehaviorMetrics: EncryptedData?, // Usage patterns, consistency
    val interactionHistory: EncryptedData?, // How user interacts with verifiers
    val trustDecisionHistory: EncryptedData?, // What they've shared with whom
    
    // Metadata
    val encryptionKeyId: String, // Which key was used to encrypt
    val vaultVersion: Int = 1, // Schema version for future migrations
    val lastAccessed: Instant
)

/**
 * Encrypted data container with metadata
 */
@Serializable
data class EncryptedData(
    val ciphertext: String, // Base64 encrypted data
    val algorithm: String = "AES-256-GCM", // Encryption algorithm used
    val keyDerivation: String = "PBKDF2", // Key derivation method
    val iv: String, // Initialization vector
    val authTag: String, // Authentication tag
    val dataType: String, // What type of data this contains
    val encryptedAt: Instant,
    val dataHash: String // Hash of plaintext for integrity checking
)

/**
 * Comprehensive quality profile from enhanced Veriff integration
 */
@Serializable
data class CredentialQualityProfile(
    // Overall Quality Assessment
    val qualityLevel: VerificationLevel, // Gold, Premium, Standard, Basic
    val overallScore: Float, // 0.0 to 1.0 composite quality score
    val confidenceLevel: String, // "Very High", "High", "Medium", "Low"
    
    // Identity Verification Quality
    val identityVerification: IdentityQualityMetrics,
    
    // Document Verification Quality
    val documentVerification: DocumentQualityMetrics,
    
    // Biometric Verification Quality  
    val biometricVerification: BiometricQualityMetrics,
    
    // Risk Assessment
    val riskAssessment: RiskQualityMetrics,
    
    // Verification Context
    val verificationContext: VerificationContextMetrics,
    
    // Quality timestamp and version
    val assessedAt: Instant,
    val veriffSessionId: String,
    val qualityVersion: String = "v2.0"
)

@Serializable
data class IdentityQualityMetrics(
    val nameConfidence: Float, // 0.0 to 1.0
    val dateOfBirthConfidence: Float,
    val addressConfidence: Float,
    val crossReferenceScore: Float, // How well data cross-references
    val consistencyScore: Float, // Internal consistency of provided data
    val historicalVerification: Boolean, // Has historical verification data
    val governmentIdMatch: Boolean // Matches government database (where available)
)

@Serializable  
data class DocumentQualityMetrics(
    val documentType: String, // "passport", "driving_license", "national_id"
    val authenticity: Float, // 0.0 to 1.0 - document authenticity score
    val imageQuality: Float, // Quality of document images
    val ocrConfidence: Float, // OCR extraction confidence
    val securityFeatures: SecurityFeaturesVerification,
    val documentAge: DocumentAge, // How recent is the document
    val issuerVerification: IssuerVerificationMetrics
)

@Serializable
data class SecurityFeaturesVerification(
    val hologramsDetected: Boolean,
    val watermarksVerified: Boolean, 
    val microTextReadable: Boolean,
    val rfidChipRead: Boolean,
    val overallSecurityScore: Float
)

@Serializable
data class DocumentAge(
    val issueDate: String?, // When document was issued
    val expiryDate: String?, // When document expires  
    val ageInMonths: Int, // How old the document is
    val freshnessScore: Float // 1.0 for very fresh, 0.0 for old
)

@Serializable
data class IssuerVerificationMetrics(
    val issuerRecognized: Boolean, // Is issuing authority recognized
    val issuerTrustScore: Float, // Trust level of issuing authority
    val issuerCountry: String, // Country of issuing authority
    val crossBorderValid: Boolean // Valid across borders
)

@Serializable
data class BiometricQualityMetrics(
    val livenessScore: Float, // 0.0 to 1.0 - liveness detection confidence
    val faceQuality: Float, // Quality of facial biometric
    val faceConfidence: Float, // Confidence in face match
    val spoofingDetection: SpoofingDetectionMetrics,
    val biometricUniqueness: Float, // How unique the biometric is
    val templateQuality: Float // Quality of generated biometric template
)

@Serializable
data class SpoofingDetectionMetrics(
    val screenDetection: Boolean, // Detected screen/display
    val maskDetection: Boolean, // Detected mask usage
    val photoDetection: Boolean, // Detected photo usage
    val videoDetection: Boolean, // Detected pre-recorded video
    val deepfakeScore: Float, // 0.0 to 1.0 - deepfake likelihood
    val overallSpoofScore: Float // Combined spoofing risk score
)

@Serializable
data class RiskQualityMetrics(
    val overallRiskScore: Float, // 0.0 (low risk) to 1.0 (high risk)
    val fraudIndicators: List<String>, // List of detected fraud signals
    val sanctionsCheck: SanctionsCheckResult,
    val pepsCheck: PEPsCheckResult, // Politically Exposed Persons
    val deviceRiskAssessment: DeviceRiskMetrics,
    val behavioralRiskScore: Float, // Risk based on verification behavior
    val geolocationRisk: GeolocationRiskMetrics
)

@Serializable
data class SanctionsCheckResult(
    val checked: Boolean,
    val matchFound: Boolean,
    val sanctionsList: List<String>, // Which sanctions lists checked
    val confidence: Float // Confidence in the check result
)

@Serializable
data class PEPsCheckResult(
    val checked: Boolean,
    val matchFound: Boolean,
    val riskLevel: String, // "Low", "Medium", "High"
    val confidence: Float
)

@Serializable
data class DeviceRiskMetrics(
    val deviceTrustScore: Float, // 0.0 to 1.0 - how much we trust the device
    val jailbrokenRooted: Boolean, // Is device jailbroken/rooted
    val emulatorDetected: Boolean, // Running on emulator
    val vpnDetected: Boolean, // Using VPN
    val proxyDetected: Boolean, // Using proxy
    val deviceFingerprint: String // Unique device identifier
)

@Serializable
data class GeolocationRiskMetrics(
    val locationConsistent: Boolean, // Location consistent with claimed identity
    val highRiskCountry: Boolean, // Location in high-risk country
    val locationSpoofed: Boolean, // Location appears spoofed
    val travelPatternNormal: Boolean, // Normal travel pattern
    val locationConfidence: Float // Confidence in location data
)

@Serializable
data class VerificationContextMetrics(
    val sessionDuration: Long, // How long verification took (seconds)
    val attemptCount: Int, // Number of attempts needed
    val userCooperation: Float, // 0.0 to 1.0 - how cooperative user was
    val technicalQuality: Float, // Technical quality of session
    val completionRate: Float, // What % of verification was completed
    val verificationMethod: String, // "mobile_app", "web", etc.
    val operatorReview: Boolean, // Was human review required
    val aiConfidence: Float // AI confidence in automated decision
)

/**
 * Privacy budget tracking - what's been shared with whom
 */
@Serializable
data class PrivacyBudget(
    val totalShares: Int, // Total number of times data has been shared
    val shareHistory: List<PrivacyShare>, // History of what was shared
    val riskBudget: Float, // Remaining privacy budget (0.0 to 1.0)
    val alertThresholds: PrivacyAlertThresholds,
    val budgetResetDate: Instant? // When budget resets (if applicable)
)

@Serializable
data class PrivacyShare(
    val shareId: String,
    val rpIdentifier: String,
    val rpDisplayName: String,
    val predicatesShared: List<String>, // What predicates were proven
    val shareTimestamp: Instant,
    val purpose: String, // Why it was shared
    val retentionPeriod: Int, // How long RP will retain (days)
    val dataMinimization: Boolean, // Was data minimized
    val consentReceiptId: String // Link to consent receipt
)

@Serializable
data class PrivacyAlertThresholds(
    val maxSharesPerDay: Int = 5,
    val maxSharesPerRP: Int = 10, 
    val maxSensitiveShares: Int = 3, // High-risk predicates
    val riskBudgetWarning: Float = 0.3f, // Warn when budget below this
    val suspiciousPatternDetection: Boolean = true
)

/**
 * Enhanced predicate that can be derived from sensitive vault with additional metadata
 */
@Serializable
data class EnhancedPredicate(
    val id: String,
    val description: String,
    val canProve: Boolean,
    val requiresConsent: Boolean = true,
    
    // Enhanced predicate metadata
    val confidenceLevel: Float, // 0.0 to 1.0 - confidence we can prove this
    val privacyRisk: PrivacyRiskLevel, // How risky is proving this predicate
    val dataSource: List<String>, // Which vault data sources enable this
    val qualityRequirement: EnhancedVerificationLevel, // Minimum quality needed
    val lastVerified: Instant, // When this predicate was last verified
    val expiryDate: Instant?, // When this predicate expires (if applicable)
    
    // Legal and compliance
    val jurisdictionSupport: List<String>, // Which jurisdictions recognize this
    val complianceFlags: List<String>, // GDPR, CCPA, etc.
    val auditTrail: Boolean // Whether proving this creates audit trail
)

/**
 * Privacy risk levels for predicates
 */
enum class PrivacyRiskLevel {
    MINIMAL, // Age ≥ 18, basic verification status
    LOW, // Income bracket, general location  
    MEDIUM, // Specific qualifications, detailed location
    HIGH, // Specific income, detailed personal info
    CRITICAL // Biometrics, full identity, sensitive personal data
}

/**
 * Enhanced verification levels with richer quality tiers
 */
enum class EnhancedVerificationLevel(
    val displayName: String,
    val emoji: String,
    val minQualityScore: Float,
    val description: String
) {
    BASIC("Basic", "🔵", 0.0f, "Minimal verification with basic identity checks"),
    STANDARD("Standard", "🟡", 0.6f, "Standard verification with document validation"),
    PREMIUM("Premium", "🟠", 0.8f, "Premium verification with biometric validation"),
    GOLD("Gold", "🟢", 0.9f, "Gold verification with comprehensive risk assessment"),
    PLATINUM("Platinum", "💎", 0.95f, "Platinum verification with ongoing monitoring")
}