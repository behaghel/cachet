package main

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/rs/zerolog/log"
)

// OpenID4VCI data structures
type TokenRequest struct {
	GrantType string `json:"grant_type"`
	ClientID  string `json:"client_id"`
	Scope     string `json:"scope"`
}

type TokenResponse struct {
	AccessToken string `json:"access_token"`
	TokenType   string `json:"token_type"`
	ExpiresIn   int    `json:"expires_in"`
	Scope       string `json:"scope"`
}

type CredentialRequest struct {
	Format string                 `json:"format"`
	Types  []string               `json:"types"`
	Proof  map[string]interface{} `json:"proof,omitempty"`
}

type CredentialResponse struct {
	Credential interface{} `json:"credential"`
	Format     string      `json:"format"`
}

// Veriff webhook data structures
// Enhanced VeriffSession struct for Phase A Professional API integration
type VeriffSession struct {
	SessionID       string `json:"session_id"`
	Status          string `json:"status"`
	Id              string `json:"id"`
	Url             string `json:"url,omitempty"`
	VerificationUrl string `json:"verification_url,omitempty"`

	Person struct {
		FirstName             string  `json:"firstName"`
		LastName              string  `json:"lastName"`
		FullName              string  `json:"fullName,omitempty"`
		DateOfBirth           string  `json:"dateOfBirth"`
		Nationality           string  `json:"nationality,omitempty"`
		Gender                string  `json:"gender,omitempty"`
		Confidence            float64 `json:"confidence,omitempty"`
		FirstNameConfidence   float64 `json:"firstName_confidence,omitempty"`
		DateOfBirthConfidence float64 `json:"dateOfBirth_confidence,omitempty"`
	} `json:"person"`

	Document struct {
		Number           string  `json:"number"`
		Type             string  `json:"type"`
		Country          string  `json:"country"`
		FirstName        string  `json:"firstName,omitempty"`
		LastName         string  `json:"lastName,omitempty"`
		DateOfBirth      string  `json:"dateOfBirth,omitempty"`
		IssueDate        string  `json:"issueDate,omitempty"`
		ExpiryDate       string  `json:"expiryDate,omitempty"`
		Authenticity     float64 `json:"authenticity,omitempty"`
		ImageQuality     float64 `json:"imageQuality,omitempty"`
		OcrConfidence    float64 `json:"ocrConfidence,omitempty"`
		IssuerRecognized bool    `json:"issuerRecognized,omitempty"`
		IssuerTrustScore float64 `json:"issuerTrustScore,omitempty"`
		CrossBorderValid bool    `json:"crossBorderValid,omitempty"`
		FrontImage       string  `json:"frontImage,omitempty"`
		BackImage        string  `json:"backImage,omitempty"`
		SecurityFeatures struct {
			Holograms    bool    `json:"holograms,omitempty"`
			Watermarks   bool    `json:"watermarks,omitempty"`
			MicroText    bool    `json:"microText,omitempty"`
			RfidRead     bool    `json:"rfidRead,omitempty"`
			OverallScore float64 `json:"overallScore,omitempty"`
		} `json:"securityFeatures,omitempty"`
	} `json:"document"`

	Face struct {
		Image             string                 `json:"image,omitempty"`
		Quality           float64                `json:"quality,omitempty"`
		Confidence        float64                `json:"confidence,omitempty"`
		UniquenessScore   float64                `json:"uniquenessScore,omitempty"`
		TemplateQuality   float64                `json:"templateQuality,omitempty"`
		Template          string                 `json:"template,omitempty"`
		QualityMetrics    map[string]interface{} `json:"qualityMetrics,omitempty"`
		UniquenessVector  map[string]interface{} `json:"uniquenessVector,omitempty"`
		SpoofingDetection struct {
			Screen        bool    `json:"screen,omitempty"`
			Mask          bool    `json:"mask,omitempty"`
			Photo         bool    `json:"photo,omitempty"`
			Video         bool    `json:"video,omitempty"`
			DeepfakeScore float64 `json:"deepfakeScore,omitempty"`
			OverallScore  float64 `json:"overallScore,omitempty"`
		} `json:"spoofingDetection,omitempty"`
	} `json:"face,omitempty"`

	Verification struct {
		LivenessScore     float64 `json:"liveness_score,omitempty"`
		OverallConfidence float64 `json:"overall_confidence,omitempty"`
		RiskScore         float64 `json:"risk_score,omitempty"`
		Timestamp         string  `json:"timestamp,omitempty"`
	} `json:"verification,omitempty"`

	Risk struct {
		FraudIndicators     []string               `json:"fraudIndicators,omitempty"`
		RiskFactors         []string               `json:"riskFactors,omitempty"`
		BehavioralScore     float64                `json:"behavioralScore,omitempty"`
		SanctionsChecked    bool                   `json:"sanctionsChecked,omitempty"`
		SanctionsMatch      bool                   `json:"sanctionsMatch,omitempty"`
		SanctionsLists      []string               `json:"sanctionsLists,omitempty"`
		SanctionsConfidence float64                `json:"sanctionsConfidence,omitempty"`
		PepsChecked         bool                   `json:"pepsChecked,omitempty"`
		PepsMatch           bool                   `json:"pepsMatch,omitempty"`
		PepsRiskLevel       string                 `json:"pepsRiskLevel,omitempty"`
		PepsConfidence      float64                `json:"pepsConfidence,omitempty"`
		BehaviorAnalysis    map[string]interface{} `json:"behaviorAnalysis,omitempty"`
		AnomalyDetection    map[string]interface{} `json:"anomalyDetection,omitempty"`
	} `json:"risk,omitempty"`

	Device struct {
		UserAgent        string  `json:"userAgent,omitempty"`
		IpAddress        string  `json:"ipAddress,omitempty"`
		Fingerprint      string  `json:"fingerprint,omitempty"`
		ScreenSize       string  `json:"screenSize,omitempty"`
		Timezone         string  `json:"timezone,omitempty"`
		TrustScore       float64 `json:"trustScore,omitempty"`
		JailbrokenRooted bool    `json:"jailbrokenRooted,omitempty"`
		EmulatorDetected bool    `json:"emulatorDetected,omitempty"`
		VpnDetected      bool    `json:"vpnDetected,omitempty"`
		ProxyDetected    bool    `json:"proxyDetected,omitempty"`
	} `json:"device,omitempty"`

	Geolocation struct {
		ConsistentWithId    bool    `json:"consistentWithId,omitempty"`
		HighRiskCountry     bool    `json:"highRiskCountry,omitempty"`
		Spoofed             bool    `json:"spoofed,omitempty"`
		TravelPatternNormal bool    `json:"travelPatternNormal,omitempty"`
		Confidence          float64 `json:"confidence,omitempty"`
	} `json:"geolocation,omitempty"`

	// Session context data
	SessionDuration        int64             `json:"sessionDuration,omitempty"`
	AttemptCount           int               `json:"attemptCount,omitempty"`
	UserCooperationScore   float64           `json:"userCooperationScore,omitempty"`
	TechnicalQualityScore  float64           `json:"technicalQualityScore,omitempty"`
	CompletionRate         float64           `json:"completionRate,omitempty"`
	Method                 string            `json:"method,omitempty"`
	RequiredOperatorReview bool              `json:"requiredOperatorReview,omitempty"`
	AiConfidenceScore      float64           `json:"aiConfidenceScore,omitempty"`
	Timestamps             map[string]string `json:"timestamps,omitempty"`
	Attempts               []string          `json:"attempts,omitempty"`
	DecisionReasons        []string          `json:"decisionReasons,omitempty"`
}

// Verifiable Credential structures (simplified SD-JWT VC)
type VerifiableCredential struct {
	Context           []string               `json:"@context"`
	ID                string                 `json:"id"`
	Type              []string               `json:"type"`
	Issuer            string                 `json:"issuer"`
	IssuanceDate      string                 `json:"issuanceDate"`
	ExpirationDate    string                 `json:"expirationDate,omitempty"`
	CredentialSubject map[string]interface{} `json:"credentialSubject"`
	CredentialStatus  *CredentialStatus      `json:"credentialStatus,omitempty"`
}

type CredentialStatus struct {
	ID   string `json:"id"`
	Type string `json:"type"`
}

// Quality validation structures
type ValidationResult struct {
	IsValid      bool    `json:"is_valid"`
	Reason       string  `json:"reason,omitempty"`
	QualityLevel string  `json:"quality_level"`
	Confidence   float64 `json:"confidence"`
}

// Enhanced validation result for Phase A
type EnhancedValidationResult struct {
	IsValid        bool                     `json:"is_valid"`
	Reason         string                   `json:"reason,omitempty"`
	QualityLevel   string                   `json:"quality_level"`
	QualityProfile CredentialQualityProfile `json:"quality_profile"`
	SensitiveData  map[string]interface{}   `json:"sensitive_data,omitempty"`
}

// Enhanced credential quality profile structures
type CredentialQualityProfile struct {
	QualityLevel          string                     `json:"quality_level"`
	OverallScore          float64                    `json:"overall_score"`
	ConfidenceLevel       string                     `json:"confidence_level"`
	IdentityVerification  IdentityQualityMetrics     `json:"identity_verification"`
	DocumentVerification  DocumentQualityMetrics     `json:"document_verification"`
	BiometricVerification BiometricQualityMetrics    `json:"biometric_verification"`
	RiskAssessment        RiskQualityMetrics         `json:"risk_assessment"`
	VerificationContext   VerificationContextMetrics `json:"verification_context"`
	AssessedAt            time.Time                  `json:"assessed_at"`
	VeriffSessionId       string                     `json:"veriff_session_id"`
	QualityVersion        string                     `json:"quality_version"`
}

type IdentityQualityMetrics struct {
	NameConfidence         float64 `json:"name_confidence"`
	DateOfBirthConfidence  float64 `json:"date_of_birth_confidence"`
	AddressConfidence      float64 `json:"address_confidence"`
	CrossReferenceScore    float64 `json:"cross_reference_score"`
	ConsistencyScore       float64 `json:"consistency_score"`
	HistoricalVerification bool    `json:"historical_verification"`
	GovernmentIdMatch      bool    `json:"government_id_match"`
}

type DocumentQualityMetrics struct {
	DocumentType       string                       `json:"document_type"`
	Authenticity       float64                      `json:"authenticity"`
	ImageQuality       float64                      `json:"image_quality"`
	OcrConfidence      float64                      `json:"ocr_confidence"`
	SecurityFeatures   SecurityFeaturesVerification `json:"security_features"`
	DocumentAge        DocumentAge                  `json:"document_age"`
	IssuerVerification IssuerVerificationMetrics    `json:"issuer_verification"`
}

type SecurityFeaturesVerification struct {
	HologramsDetected    bool    `json:"holograms_detected"`
	WatermarksVerified   bool    `json:"watermarks_verified"`
	MicroTextReadable    bool    `json:"micro_text_readable"`
	RfidChipRead         bool    `json:"rfid_chip_read"`
	OverallSecurityScore float64 `json:"overall_security_score"`
}

type DocumentAge struct {
	IssueDate      string  `json:"issue_date,omitempty"`
	ExpiryDate     string  `json:"expiry_date,omitempty"`
	AgeInMonths    int     `json:"age_in_months"`
	FreshnessScore float64 `json:"freshness_score"`
}

type IssuerVerificationMetrics struct {
	IssuerRecognized bool    `json:"issuer_recognized"`
	IssuerTrustScore float64 `json:"issuer_trust_score"`
	IssuerCountry    string  `json:"issuer_country"`
	CrossBorderValid bool    `json:"cross_border_valid"`
}

type BiometricQualityMetrics struct {
	LivenessScore       float64                  `json:"liveness_score"`
	FaceQuality         float64                  `json:"face_quality"`
	FaceConfidence      float64                  `json:"face_confidence"`
	BiometricUniqueness float64                  `json:"biometric_uniqueness"`
	TemplateQuality     float64                  `json:"template_quality"`
	SpoofingDetection   SpoofingDetectionMetrics `json:"spoofing_detection"`
}

type SpoofingDetectionMetrics struct {
	ScreenDetection   bool    `json:"screen_detection"`
	MaskDetection     bool    `json:"mask_detection"`
	PhotoDetection    bool    `json:"photo_detection"`
	VideoDetection    bool    `json:"video_detection"`
	DeepfakeScore     float64 `json:"deepfake_score"`
	OverallSpoofScore float64 `json:"overall_spoof_score"`
}

type RiskQualityMetrics struct {
	OverallRiskScore     float64                `json:"overall_risk_score"`
	FraudIndicators      []string               `json:"fraud_indicators"`
	BehavioralRiskScore  float64                `json:"behavioral_risk_score"`
	SanctionsCheck       SanctionsCheckResult   `json:"sanctions_check"`
	PepsCheck            PEPsCheckResult        `json:"peps_check"`
	DeviceRiskAssessment DeviceRiskMetrics      `json:"device_risk_assessment"`
	GeolocationRisk      GeolocationRiskMetrics `json:"geolocation_risk"`
}

type SanctionsCheckResult struct {
	Checked       bool     `json:"checked"`
	MatchFound    bool     `json:"match_found"`
	SanctionsList []string `json:"sanctions_list"`
	Confidence    float64  `json:"confidence"`
}

type PEPsCheckResult struct {
	Checked    bool    `json:"checked"`
	MatchFound bool    `json:"match_found"`
	RiskLevel  string  `json:"risk_level"`
	Confidence float64 `json:"confidence"`
}

type DeviceRiskMetrics struct {
	DeviceTrustScore  float64 `json:"device_trust_score"`
	JailbrokenRooted  bool    `json:"jailbroken_rooted"`
	EmulatorDetected  bool    `json:"emulator_detected"`
	VpnDetected       bool    `json:"vpn_detected"`
	ProxyDetected     bool    `json:"proxy_detected"`
	DeviceFingerprint string  `json:"device_fingerprint"`
}

type GeolocationRiskMetrics struct {
	LocationConsistent  bool    `json:"location_consistent"`
	HighRiskCountry     bool    `json:"high_risk_country"`
	LocationSpoofed     bool    `json:"location_spoofed"`
	TravelPatternNormal bool    `json:"travel_pattern_normal"`
	LocationConfidence  float64 `json:"location_confidence"`
}

type VerificationContextMetrics struct {
	SessionDuration    int64   `json:"session_duration"`
	AttemptCount       int     `json:"attempt_count"`
	UserCooperation    float64 `json:"user_cooperation"`
	TechnicalQuality   float64 `json:"technical_quality"`
	CompletionRate     float64 `json:"completion_rate"`
	VerificationMethod string  `json:"verification_method"`
	OperatorReview     bool    `json:"operator_review"`
	AiConfidence       float64 `json:"ai_confidence"`
}

// Verification level enumeration
const (
	VerificationLevelBasic    = "basic"
	VerificationLevelStandard = "standard"
	VerificationLevelPremium  = "premium"
	VerificationLevelGold     = "gold"
)

type Server struct {
	router           *chi.Mux
	signingKey       *rsa.PrivateKey
	accessTokens     map[string]TokenInfo     // In-memory token store (production should use Redis)
	verifiedSessions map[string]VeriffSession // Store for verified Veriff sessions
}

type TokenInfo struct {
	ClientID  string
	Scope     string
	ExpiresAt time.Time
}

func NewServer() *Server {
	// Generate RSA key for JWT signing (in production, load from secure storage)
	signingKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to generate RSA key")
	}

	s := &Server{
		router:           chi.NewRouter(),
		signingKey:       signingKey,
		accessTokens:     make(map[string]TokenInfo),
		verifiedSessions: make(map[string]VeriffSession),
	}

	s.setupMiddleware()
	s.setupRoutes()
	return s
}

func (s *Server) setupMiddleware() {
	s.router.Use(middleware.RequestID)
	s.router.Use(middleware.RealIP)
	s.router.Use(middleware.Logger)
	s.router.Use(middleware.Recoverer)
}

func (s *Server) setupRoutes() {
	// Note: /healthz is reserved by Cloud Run infrastructure - use /health instead
	s.router.Get("/health", s.handleHealth)

	// OpenID4VCI endpoints
	s.router.Post("/oauth/token", s.handleOAuthToken)
	s.router.Post("/credential", s.handleCredentialIssuance)

	// Veriff webhook
	s.router.Post("/webhooks/veriff", s.handleVeriffWebhook)
}

// EnhancedVeriffValidation performs comprehensive validation for gold quality credentials
func validateVeriffSessionEnhanced(session VeriffSession) EnhancedValidationResult {
	if session.Status != "approved" {
		return EnhancedValidationResult{
			IsValid:      false,
			Reason:       "Veriff session not approved",
			QualityLevel: "none",
			QualityProfile: CredentialQualityProfile{
				OverallScore:    0.0,
				ConfidenceLevel: "Low",
			},
		}
	}

	// Build comprehensive quality profile
	qualityProfile := buildQualityProfile(session)

	// Calculate overall quality level based on enhanced metrics
	qualityLevel := determineQualityLevel(qualityProfile)

	// Enhanced validation checks for gold tier
	if qualityLevel == "gold" {
		if err := validateGoldTierRequirements(session, qualityProfile); err != nil {
			return EnhancedValidationResult{
				IsValid:        false,
				Reason:         err.Error(),
				QualityLevel:   "premium", // Downgrade to premium
				QualityProfile: qualityProfile,
			}
		}
	}

	return EnhancedValidationResult{
		IsValid:        true,
		QualityLevel:   qualityLevel,
		QualityProfile: qualityProfile,
		SensitiveData:  extractSensitiveData(session),
	}
}

// buildQualityProfile creates comprehensive quality assessment from Veriff data
func buildQualityProfile(session VeriffSession) CredentialQualityProfile {
	// Identity verification quality
	identityMetrics := IdentityQualityMetrics{
		NameConfidence:         session.Person.FirstNameConfidence,
		DateOfBirthConfidence:  session.Person.DateOfBirthConfidence,
		AddressConfidence:      0.8, // Default since Veriff doesn't provide address confidence
		CrossReferenceScore:    session.Verification.OverallConfidence,
		ConsistencyScore:       calculateConsistencyScore(session),
		HistoricalVerification: false, // Would need additional data source
		GovernmentIdMatch:      session.Document.Type != "",
	}

	// Document verification quality
	documentMetrics := DocumentQualityMetrics{
		DocumentType:  session.Document.Type,
		Authenticity:  session.Document.Authenticity,
		ImageQuality:  session.Document.ImageQuality,
		OcrConfidence: session.Document.OcrConfidence,
		SecurityFeatures: SecurityFeaturesVerification{
			HologramsDetected:    session.Document.SecurityFeatures.Holograms,
			WatermarksVerified:   session.Document.SecurityFeatures.Watermarks,
			MicroTextReadable:    session.Document.SecurityFeatures.MicroText,
			RfidChipRead:         session.Document.SecurityFeatures.RfidRead,
			OverallSecurityScore: session.Document.SecurityFeatures.OverallScore,
		},
		DocumentAge: DocumentAge{
			IssueDate:      session.Document.IssueDate,
			ExpiryDate:     session.Document.ExpiryDate,
			AgeInMonths:    calculateDocumentAge(session.Document.IssueDate),
			FreshnessScore: calculateFreshnessScore(session.Document.IssueDate),
		},
		IssuerVerification: IssuerVerificationMetrics{
			IssuerRecognized: session.Document.IssuerRecognized,
			IssuerTrustScore: session.Document.IssuerTrustScore,
			IssuerCountry:    session.Document.Country,
			CrossBorderValid: session.Document.CrossBorderValid,
		},
	}

	// Biometric verification quality
	biometricMetrics := BiometricQualityMetrics{
		LivenessScore:       session.Verification.LivenessScore,
		FaceQuality:         session.Face.Quality,
		FaceConfidence:      session.Face.Confidence,
		BiometricUniqueness: session.Face.UniquenessScore,
		TemplateQuality:     session.Face.TemplateQuality,
		SpoofingDetection: SpoofingDetectionMetrics{
			ScreenDetection:   session.Face.SpoofingDetection.Screen,
			MaskDetection:     session.Face.SpoofingDetection.Mask,
			PhotoDetection:    session.Face.SpoofingDetection.Photo,
			VideoDetection:    session.Face.SpoofingDetection.Video,
			DeepfakeScore:     session.Face.SpoofingDetection.DeepfakeScore,
			OverallSpoofScore: session.Face.SpoofingDetection.OverallScore,
		},
	}

	// Risk assessment
	riskMetrics := RiskQualityMetrics{
		OverallRiskScore:    session.Verification.RiskScore,
		FraudIndicators:     session.Risk.FraudIndicators,
		BehavioralRiskScore: session.Risk.BehavioralScore,
		SanctionsCheck: SanctionsCheckResult{
			Checked:       session.Risk.SanctionsChecked,
			MatchFound:    session.Risk.SanctionsMatch,
			SanctionsList: session.Risk.SanctionsLists,
			Confidence:    session.Risk.SanctionsConfidence,
		},
		PepsCheck: PEPsCheckResult{
			Checked:    session.Risk.PepsChecked,
			MatchFound: session.Risk.PepsMatch,
			RiskLevel:  session.Risk.PepsRiskLevel,
			Confidence: session.Risk.PepsConfidence,
		},
		DeviceRiskAssessment: DeviceRiskMetrics{
			DeviceTrustScore:  session.Device.TrustScore,
			JailbrokenRooted:  session.Device.JailbrokenRooted,
			EmulatorDetected:  session.Device.EmulatorDetected,
			VpnDetected:       session.Device.VpnDetected,
			ProxyDetected:     session.Device.ProxyDetected,
			DeviceFingerprint: session.Device.Fingerprint,
		},
		GeolocationRisk: GeolocationRiskMetrics{
			LocationConsistent:  session.Geolocation.ConsistentWithId,
			HighRiskCountry:     session.Geolocation.HighRiskCountry,
			LocationSpoofed:     session.Geolocation.Spoofed,
			TravelPatternNormal: session.Geolocation.TravelPatternNormal,
			LocationConfidence:  session.Geolocation.Confidence,
		},
	}

	// Verification context
	contextMetrics := VerificationContextMetrics{
		SessionDuration:    session.SessionDuration,
		AttemptCount:       session.AttemptCount,
		UserCooperation:    session.UserCooperationScore,
		TechnicalQuality:   session.TechnicalQualityScore,
		CompletionRate:     session.CompletionRate,
		VerificationMethod: session.Method,
		OperatorReview:     session.RequiredOperatorReview,
		AiConfidence:       session.AiConfidenceScore,
	}

	// Calculate overall score
	overallScore := calculateOverallQualityScore(identityMetrics, documentMetrics, biometricMetrics, riskMetrics)

	return CredentialQualityProfile{
		QualityLevel:          determineQualityLevelEnum(overallScore),
		OverallScore:          overallScore,
		ConfidenceLevel:       determineConfidenceLevel(overallScore),
		IdentityVerification:  identityMetrics,
		DocumentVerification:  documentMetrics,
		BiometricVerification: biometricMetrics,
		RiskAssessment:        riskMetrics,
		VerificationContext:   contextMetrics,
		AssessedAt:            time.Now(),
		VeriffSessionId:       session.Id,
		QualityVersion:        "v2.0",
	}
}

// validateVeriffSession performs quality validation on Veriff session data (legacy support)
func validateVeriffSession(session VeriffSession) ValidationResult {
	enhanced := validateVeriffSessionEnhanced(session)
	return ValidationResult{
		IsValid:      enhanced.IsValid,
		Reason:       enhanced.Reason,
		QualityLevel: enhanced.QualityLevel,
		Confidence:   enhanced.QualityProfile.OverallScore,
	}
}

// Helper functions for enhanced validation

// calculateConsistencyScore evaluates internal data consistency
func calculateConsistencyScore(session VeriffSession) float64 {
	score := 1.0

	// Check if document data matches person data
	if session.Document.FirstName != "" && session.Person.FirstName != "" {
		if session.Document.FirstName != session.Person.FirstName {
			score -= 0.2
		}
	}

	if session.Document.LastName != "" && session.Person.LastName != "" {
		if session.Document.LastName != session.Person.LastName {
			score -= 0.2
		}
	}

	// Check date consistency
	if session.Document.DateOfBirth != "" && session.Person.DateOfBirth != "" {
		if session.Document.DateOfBirth != session.Person.DateOfBirth {
			score -= 0.3
		}
	}

	return math.Max(0.0, score)
}

// calculateDocumentAge returns document age in months
func calculateDocumentAge(issueDateStr string) int {
	if issueDateStr == "" {
		return 0
	}

	issueDate, err := time.Parse("2006-01-02", issueDateStr)
	if err != nil {
		return 0
	}

	now := time.Now()
	months := int(now.Sub(issueDate).Hours() / (24 * 30))
	return months
}

// calculateFreshnessScore gives higher scores to more recent documents
func calculateFreshnessScore(issueDateStr string) float64 {
	age := calculateDocumentAge(issueDateStr)
	if age == 0 {
		return 0.5 // Unknown age gets neutral score
	}

	// Fresh documents (< 6 months) get high scores
	if age < 6 {
		return 1.0
	}

	// Decline score over time, minimum 0.2 for very old documents
	score := 1.0 - (float64(age) / 60.0) // Decline over 5 years
	return math.Max(0.2, score)
}

// calculateOverallQualityScore computes weighted overall quality
func calculateOverallQualityScore(identity IdentityQualityMetrics, document DocumentQualityMetrics, biometric BiometricQualityMetrics, risk RiskQualityMetrics) float64 {
	// Weighted scoring - higher weights for more critical factors
	identityScore := (identity.NameConfidence + identity.DateOfBirthConfidence + identity.CrossReferenceScore) / 3.0
	documentScore := (document.Authenticity + document.ImageQuality + document.OcrConfidence) / 3.0
	biometricScore := (biometric.LivenessScore + biometric.FaceConfidence + biometric.TemplateQuality) / 3.0
	riskScore := 1.0 - risk.OverallRiskScore // Invert risk score (lower risk = higher quality)

	// Weighted average (biometrics and risk are weighted higher for security)
	weighted := (identityScore*0.2 + documentScore*0.25 + biometricScore*0.35 + riskScore*0.2)

	return math.Min(1.0, math.Max(0.0, weighted))
}

// determineQualityLevel determines quality tier from score
func determineQualityLevel(profile CredentialQualityProfile) string {
	score := profile.OverallScore

	switch {
	case score >= 0.95:
		return "platinum"
	case score >= 0.90:
		return "gold"
	case score >= 0.80:
		return "premium"
	case score >= 0.60:
		return "standard"
	default:
		return "basic"
	}
}

// determineQualityLevelEnum converts score to enum value
func determineQualityLevelEnum(score float64) string {
	switch {
	case score >= 0.95:
		return "PLATINUM"
	case score >= 0.90:
		return "GOLD"
	case score >= 0.80:
		return "PREMIUM"
	case score >= 0.60:
		return "STANDARD"
	default:
		return "BASIC"
	}
}

// determineConfidenceLevel converts score to confidence description
func determineConfidenceLevel(score float64) string {
	switch {
	case score >= 0.90:
		return "Very High"
	case score >= 0.75:
		return "High"
	case score >= 0.60:
		return "Medium"
	default:
		return "Low"
	}
}

// validateGoldTierRequirements enforces strict requirements for gold tier
func validateGoldTierRequirements(session VeriffSession, profile CredentialQualityProfile) error {
	// Gold tier requires minimum thresholds across all categories
	if profile.BiometricVerification.LivenessScore < 0.90 {
		return fmt.Errorf("liveness score too low for gold tier: %.2f", profile.BiometricVerification.LivenessScore)
	}

	if profile.DocumentVerification.Authenticity < 0.95 {
		return fmt.Errorf("document authenticity too low for gold tier: %.2f", profile.DocumentVerification.Authenticity)
	}

	if profile.RiskAssessment.OverallRiskScore > 0.10 {
		return fmt.Errorf("risk score too high for gold tier: %.2f", profile.RiskAssessment.OverallRiskScore)
	}

	// Must have biometric template for gold tier
	if profile.BiometricVerification.TemplateQuality < 0.85 {
		return fmt.Errorf("biometric template quality insufficient for gold tier: %.2f", profile.BiometricVerification.TemplateQuality)
	}

	// Document must have security features verified
	if profile.DocumentVerification.SecurityFeatures.OverallSecurityScore < 0.80 {
		return fmt.Errorf("security features insufficient for gold tier")
	}

	return nil
}

// extractSensitiveData extracts and encrypts sensitive vault data
func extractSensitiveData(session VeriffSession) map[string]interface{} {
	sensitiveData := make(map[string]interface{})

	// Full identity data
	sensitiveData["fullIdentity"] = map[string]interface{}{
		"firstName":   session.Person.FirstName,
		"lastName":    session.Person.LastName,
		"fullName":    session.Person.FullName,
		"dateOfBirth": session.Person.DateOfBirth,
		"nationality": session.Person.Nationality,
		"gender":      session.Person.Gender,
	}

	// Document images (would contain base64 encoded images)
	sensitiveData["documentImages"] = map[string]interface{}{
		"frontImage": session.Document.FrontImage,
		"backImage":  session.Document.BackImage,
		"faceImage":  session.Face.Image,
	}

	// Biometric templates
	sensitiveData["biometricTemplates"] = map[string]interface{}{
		"faceTemplate":     session.Face.Template,
		"qualityMetrics":   session.Face.QualityMetrics,
		"uniquenessVector": session.Face.UniquenessVector,
	}

	// Rich verification details
	sensitiveData["verificationDetails"] = map[string]interface{}{
		"sessionId":       session.Id,
		"sessionUrl":      session.Url,
		"verificationUrl": session.VerificationUrl,
		"timestamps":      session.Timestamps,
		"attempts":        session.Attempts,
		"decisionReasons": session.DecisionReasons,
	}

	// Device and behavioral data
	sensitiveData["deviceFingerprint"] = map[string]interface{}{
		"userAgent":   session.Device.UserAgent,
		"ipAddress":   session.Device.IpAddress,
		"fingerprint": session.Device.Fingerprint,
		"screenSize":  session.Device.ScreenSize,
		"timezone":    session.Device.Timezone,
	}

	// Risk assessment details
	sensitiveData["riskAssessment"] = map[string]interface{}{
		"fraudIndicators":  session.Risk.FraudIndicators,
		"riskFactors":      session.Risk.RiskFactors,
		"behaviorAnalysis": session.Risk.BehaviorAnalysis,
		"anomalyDetection": session.Risk.AnomalyDetection,
	}

	return sensitiveData
}

// calculateAge calculates age from date of birth string (YYYY-MM-DD format)
func calculateAge(dobStr string) int {
	dob, err := time.Parse("2006-01-02", dobStr)
	if err != nil {
		return 0
	}
	now := time.Now()
	age := now.Year() - dob.Year()
	if now.YearDay() < dob.YearDay() {
		age--
	}
	return age
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Debug().Msg("Health check requested")
	w.WriteHeader(http.StatusOK)
	if _, err := w.Write([]byte("ok")); err != nil {
		log.Error().Err(err).Msg("Failed to write health check response")
	}
}

func (s *Server) handleOAuthToken(w http.ResponseWriter, r *http.Request) {
	var req TokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Error().Err(err).Msg("Failed to decode token request")
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate grant type
	if req.GrantType != "client_credentials" {
		log.Error().Str("grant_type", req.GrantType).Msg("Invalid grant type")
		http.Error(w, "Unsupported grant type", http.StatusBadRequest)
		return
	}

	// Generate access token (JWT)
	tokenID := uuid.New().String()
	now := time.Now()
	expiresAt := now.Add(time.Hour)

	claims := jwt.MapClaims{
		"sub":       req.ClientID,
		"client_id": req.ClientID,
		"scope":     req.Scope,
		"iat":       now.Unix(),
		"exp":       expiresAt.Unix(),
		"jti":       tokenID,
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	accessToken, err := token.SignedString(s.signingKey)
	if err != nil {
		log.Error().Err(err).Msg("Failed to sign access token")
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	// Store token info
	s.accessTokens[tokenID] = TokenInfo{
		ClientID:  req.ClientID,
		Scope:     req.Scope,
		ExpiresAt: expiresAt,
	}

	resp := TokenResponse{
		AccessToken: accessToken,
		TokenType:   "Bearer",
		ExpiresIn:   3600,
		Scope:       req.Scope,
	}

	log.Info().
		Str("client_id", req.ClientID).
		Str("scope", req.Scope).
		Msg("Access token issued")

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		log.Error().Err(err).Msg("Failed to encode token response")
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
}

func (s *Server) handleCredentialIssuance(w http.ResponseWriter, r *http.Request) {
	// Extract and validate bearer token
	authHeader := r.Header.Get("Authorization")
	if !strings.HasPrefix(authHeader, "Bearer ") {
		http.Error(w, "Missing or invalid authorization header", http.StatusUnauthorized)
		return
	}

	tokenString := strings.TrimPrefix(authHeader, "Bearer ")

	// Parse and validate JWT
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return &s.signingKey.PublicKey, nil
	})

	if err != nil || !token.Valid {
		log.Error().Err(err).Msg("Invalid access token")
		http.Error(w, "Invalid access token", http.StatusUnauthorized)
		return
	}

	var req CredentialRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Error().Err(err).Msg("Failed to decode credential request")
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	log.Info().
		Str("format", req.Format).
		Interface("types", req.Types).
		Msg("Credential issuance requested")

	// Create verifiable credential (simplified SD-JWT VC)
	now := time.Now()
	credentialID := fmt.Sprintf("urn:uuid:%s", uuid.New().String())

	// Find the most recent verified session (in production, this would use session ID from token)
	var veriffSession *VeriffSession
	var sessionFound bool
	for _, session := range s.verifiedSessions {
		if session.Status == "approved" {
			veriffSession = &session
			sessionFound = true
			break
		}
	}

	if !sessionFound {
		log.Error().Msg("No verified Veriff session found for credential issuance")
		http.Error(w, "No verified identity session found", http.StatusBadRequest)
		return
	}

	// Validate session quality before issuance
	validation := validateVeriffSession(*veriffSession)
	if !validation.IsValid {
		log.Error().
			Str("reason", validation.Reason).
			Str("session_id", veriffSession.SessionID).
			Msg("Veriff session failed quality validation")
		http.Error(w, fmt.Sprintf("Session validation failed: %s", validation.Reason), http.StatusBadRequest)
		return
	}

	// Calculate expiration (90 days from now for identity credentials)
	expirationDate := now.Add(90 * 24 * time.Hour)

	// Enhanced credential with quality metrics and selective disclosure support
	vc := VerifiableCredential{
		Context: []string{
			"https://www.w3.org/2018/credentials/v1",
			"https://cachet.id/contexts/identity/v1",
		},
		ID:             credentialID,
		Type:           req.Types,
		Issuer:         "did:web:cachet.id",
		IssuanceDate:   now.Format(time.RFC3339),
		ExpirationDate: expirationDate.Format(time.RFC3339),
		CredentialSubject: map[string]interface{}{
			"id": "did:example:holder", // This would come from the authenticated session

			// Personal data (selective disclosure ready)
			"personalData": map[string]interface{}{
				"age":          calculateAge(veriffSession.Person.DateOfBirth),
				"nationality":  veriffSession.Document.Country,
				"documentType": veriffSession.Document.Type,
			},

			// Verification evidence
			"verificationLevel":  validation.QualityLevel,
			"verified":           true,
			"verificationMethod": "veriff",

			// Quality metrics (for transparency, not selective disclosure)
			"verificationMetrics": map[string]interface{}{
				"overallConfidence":    validation.Confidence,
				"livenessScore":        veriffSession.Verification.LivenessScore,
				"documentAuthenticity": veriffSession.Document.Authenticity,
				"riskScore":            veriffSession.Verification.RiskScore,
				"sessionTimestamp":     veriffSession.Verification.Timestamp,
			},

			// Evidence for audit trail
			"evidence": []map[string]interface{}{
				{
					"type":      "VeriffVerification",
					"sessionId": veriffSession.SessionID,
					"verifier":  "did:veriff:production",
					"status":    veriffSession.Status,
				},
			},
		},
		CredentialStatus: &CredentialStatus{
			ID:   fmt.Sprintf("https://cachet.id/status/1#%s", uuid.New().String()),
			Type: "StatusList2021Entry",
		},
	}

	resp := CredentialResponse{
		Credential: vc,
		Format:     req.Format,
	}

	log.Info().
		Str("credential_id", credentialID).
		Msg("Credential issued successfully")

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		log.Error().Err(err).Msg("Failed to encode credential response")
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
}

func (s *Server) handleVeriffWebhook(w http.ResponseWriter, r *http.Request) {
	var session VeriffSession
	if err := json.NewDecoder(r.Body).Decode(&session); err != nil {
		log.Error().Err(err).Msg("Failed to decode Veriff webhook")
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	log.Info().
		Str("session_id", session.SessionID).
		Str("status", session.Status).
		Msg("Veriff webhook received")

	switch session.Status {
	case "approved":
		// Enhanced validation for gold quality credentials
		enhancedValidation := validateVeriffSessionEnhanced(session)

		log.Info().
			Str("session_id", session.SessionID).
			Str("quality_level", enhancedValidation.QualityLevel).
			Float64("overall_score", enhancedValidation.QualityProfile.OverallScore).
			Str("confidence_level", enhancedValidation.QualityProfile.ConfidenceLevel).
			Msg("Enhanced validation completed")

		if enhancedValidation.IsValid {
			// Store successful verification with enhanced validation results
			s.verifiedSessions[session.SessionID] = session

			// Pre-process sensitive data for privacy vault
			s.preprocessSensitiveData(session, enhancedValidation)

			log.Info().
				Str("session_id", session.SessionID).
				Str("quality_level", enhancedValidation.QualityLevel).
				Bool("gold_tier", enhancedValidation.QualityLevel == "gold").
				Msg("Verification session stored successfully")
		} else {
			log.Warn().
				Str("session_id", session.SessionID).
				Str("reason", enhancedValidation.Reason).
				Str("quality_level", enhancedValidation.QualityLevel).
				Msg("Verification session failed enhanced validation")
		}

		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte("ok")); err != nil {
			log.Error().Err(err).Msg("Failed to write webhook response")
		}
	case "declined", "expired", "abandoned":
		log.Info().
			Str("session_id", session.SessionID).
			Str("status", session.Status).
			Msg("Verification session not approved")

		w.WriteHeader(http.StatusAccepted) // Acknowledged but not processed
		if _, err := w.Write([]byte("acknowledged")); err != nil {
			log.Error().Err(err).Msg("Failed to write webhook response")
		}
	default:
		// Unknown status
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte("ok")); err != nil {
			log.Error().Err(err).Msg("Failed to write webhook response")
		}
	}
}

// preprocessSensitiveData prepares sensitive data for encryption in privacy vault
func (s *Server) preprocessSensitiveData(session VeriffSession, validation EnhancedValidationResult) {
	log.Info().
		Str("session_id", session.SessionID).
		Int("sensitive_data_fields", len(validation.SensitiveData)).
		Msg("Pre-processing sensitive data for privacy vault")

	// In production, would encrypt sensitive data here and store it securely
	// This is where the privacy vault encryption would happen before storing

	// Log quality metrics for monitoring (without sensitive data)
	log.Info().
		Str("session_id", session.SessionID).
		Float64("identity_name_confidence", validation.QualityProfile.IdentityVerification.NameConfidence).
		Float64("identity_dob_confidence", validation.QualityProfile.IdentityVerification.DateOfBirthConfidence).
		Float64("document_authenticity", validation.QualityProfile.DocumentVerification.Authenticity).
		Float64("biometric_liveness", validation.QualityProfile.BiometricVerification.LivenessScore).
		Float64("biometric_face_confidence", validation.QualityProfile.BiometricVerification.FaceConfidence).
		Float64("risk_overall_score", validation.QualityProfile.RiskAssessment.OverallRiskScore).
		Bool("sanctions_checked", validation.QualityProfile.RiskAssessment.SanctionsCheck.Checked).
		Bool("peps_checked", validation.QualityProfile.RiskAssessment.PepsCheck.Checked).
		Float64("device_trust_score", validation.QualityProfile.RiskAssessment.DeviceRiskAssessment.DeviceTrustScore).
		Int64("session_duration", validation.QualityProfile.VerificationContext.SessionDuration).
		Int("attempt_count", validation.QualityProfile.VerificationContext.AttemptCount).
		Msg("Quality metrics logged")

	// Quality tier specific processing
	switch validation.QualityLevel {
	case "gold", "platinum":
		log.Info().
			Str("session_id", session.SessionID).
			Str("tier", validation.QualityLevel).
			Msg("Processing premium tier verification - full biometric templates available")
	case "premium":
		log.Info().
			Str("session_id", session.SessionID).
			Msg("Processing premium tier verification - enhanced document validation")
	case "standard":
		log.Info().
			Str("session_id", session.SessionID).
			Msg("Processing standard tier verification - basic identity validation")
	default:
		log.Info().
			Str("session_id", session.SessionID).
			Str("tier", validation.QualityLevel).
			Msg("Processing basic tier verification")
	}
}

func (s *Server) Start(addr string) error {
	log.Info().Str("addr", addr).Msg("Issuance gateway starting")

	server := &http.Server{
		Addr:         addr,
		Handler:      s.router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	return server.ListenAndServe()
}
