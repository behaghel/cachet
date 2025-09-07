package main

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
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
type VeriffSession struct {
	SessionID string `json:"session_id"`
	Status    string `json:"status"`
	Person    struct {
		FirstName   string  `json:"firstName"`
		LastName    string  `json:"lastName"`
		DateOfBirth string  `json:"dateOfBirth"`
		Confidence  float64 `json:"confidence,omitempty"` // Quality metric
	} `json:"person"`
	Document struct {
		Number       string  `json:"number"`
		Type         string  `json:"type"`
		Country      string  `json:"country"`
		Authenticity float64 `json:"authenticity,omitempty"` // Quality metric
	} `json:"document"`
	Verification struct {
		LivenessScore     float64 `json:"liveness_score,omitempty"`
		OverallConfidence float64 `json:"overall_confidence,omitempty"`
		RiskScore         float64 `json:"risk_score,omitempty"`
		Timestamp         string  `json:"timestamp,omitempty"`
	} `json:"verification,omitempty"`
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

// validateVeriffSession performs quality validation on Veriff session data
func validateVeriffSession(session VeriffSession) ValidationResult {
	// Default to basic validation
	if session.Status != "approved" {
		return ValidationResult{
			IsValid:      false,
			Reason:       "Veriff session not approved",
			QualityLevel: "none",
			Confidence:   0.0,
		}
	}

	// Calculate overall confidence and quality level
	confidence := session.Verification.OverallConfidence
	if confidence == 0.0 && session.Person.Confidence > 0 {
		// Fallback to person confidence if overall not available
		confidence = session.Person.Confidence
	}
	if confidence == 0.0 {
		// Default confidence for approved sessions without metrics
		confidence = 0.85
	}

	// Quality level thresholds
	var qualityLevel string
	switch {
	case confidence >= 0.95 && session.Verification.LivenessScore >= 0.90 && session.Document.Authenticity >= 0.95:
		qualityLevel = VerificationLevelGold
	case confidence >= 0.90 && session.Verification.LivenessScore >= 0.85:
		qualityLevel = VerificationLevelPremium
	case confidence >= 0.80:
		qualityLevel = VerificationLevelStandard
	default:
		qualityLevel = VerificationLevelBasic
	}

	// Additional validation checks
	if session.Verification.RiskScore > 0.3 { // High risk
		return ValidationResult{
			IsValid:      false,
			Reason:       "High risk score detected",
			QualityLevel: qualityLevel,
			Confidence:   confidence,
		}
	}

	if session.Verification.LivenessScore > 0 && session.Verification.LivenessScore < 0.7 {
		return ValidationResult{
			IsValid:      false,
			Reason:       "Liveness check insufficient",
			QualityLevel: qualityLevel,
			Confidence:   confidence,
		}
	}

	return ValidationResult{
		IsValid:      true,
		QualityLevel: qualityLevel,
		Confidence:   confidence,
	}
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

	if session.Status == "approved" {
		// Validate session quality before storing
		validation := validateVeriffSession(session)

		if validation.IsValid {
			// Store successful verification with validation results
			s.verifiedSessions[session.SessionID] = session

			log.Info().
				Str("session_id", session.SessionID).
				Str("first_name", session.Person.FirstName).
				Str("doc_type", session.Document.Type).
				Str("country", session.Document.Country).
				Str("quality_level", validation.QualityLevel).
				Float64("confidence", validation.Confidence).
				Msg("Veriff session approved, validated, and stored")
		} else {
			log.Warn().
				Str("session_id", session.SessionID).
				Str("reason", validation.Reason).
				Str("quality_level", validation.QualityLevel).
				Float64("confidence", validation.Confidence).
				Msg("Veriff session approved but failed quality validation - not stored")
		}

		w.WriteHeader(http.StatusOK)
	} else {
		log.Info().
			Str("session_id", session.SessionID).
			Str("status", session.Status).
			Msg("Veriff session not approved")

		w.WriteHeader(http.StatusAccepted) // Acknowledge but don't process
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
