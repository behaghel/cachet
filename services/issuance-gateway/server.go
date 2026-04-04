package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/golang-jwt/jwt/v5"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/credential"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/oauth"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// ServerConfig holds injectable dependencies for the issuance gateway.
type ServerConfig struct {
	Common        common.ServerConfig
	SigningKey    *rsa.PrivateKey
	Sessions      veriff.SessionStore
	WebhookSecret string // HMAC-SHA256 secret for Veriff webhook signature verification
}

// DefaultServerConfig creates a config with generated key and in-memory store.
func DefaultServerConfig() ServerConfig {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to generate RSA key")
	}
	return ServerConfig{
		Common: common.ServerConfig{
			Name:    "issuance-gateway",
			Version: "0.1.0",
			Port:    "8090",
		},
		SigningKey: key,
		Sessions:   veriff.NewInMemoryStore(),
	}
}

type Server struct {
	router        *chi.Mux
	signingKey    *rsa.PrivateKey
	sessions      veriff.SessionStore
	webhookSecret string
}

func NewServer() *Server {
	return NewServerWithConfig(DefaultServerConfig())
}

func NewServerWithConfig(cfg ServerConfig) *Server {
	s := &Server{
		router:        common.NewRouter(cfg.Common),
		signingKey:    cfg.SigningKey,
		sessions:      cfg.Sessions,
		webhookSecret: cfg.WebhookSecret,
	}

	s.router.Post("/oauth/token", s.handleOAuthToken)
	s.router.Post("/credential", s.handleCredentialIssuance)
	s.router.Post("/webhooks/veriff", s.handleVeriffWebhook)

	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handleOAuthToken(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid form body")
		return
	}

	grantType := r.FormValue("grant_type")
	clientID := r.FormValue("client_id")
	scope := r.FormValue("scope")
	sessionID := r.FormValue("session_id")

	if grantType != "client_credentials" {
		common.WriteError(w, r, http.StatusBadRequest, "unsupported_grant_type", "Only client_credentials is supported")
		return
	}
	if clientID == "" {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "client_id is required")
		return
	}

	resp, err := oauth.IssueToken(s.signingKey, clientID, scope, sessionID)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("token signing failed")
		common.WriteError(w, r, http.StatusInternalServerError, "server_error", "Failed to issue token")
		return
	}

	log.Ctx(r.Context()).Info().Str("client_id", clientID).Str("scope", scope).Msg("token issued")
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleCredentialIssuance(w http.ResponseWriter, r *http.Request) {
	token, err := oauth.ValidateBearer(r, &s.signingKey.PublicKey)
	if err != nil {
		common.WriteError(w, r, http.StatusUnauthorized, "invalid_token", err.Error())
		return
	}

	var req models.CredentialRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	// Validate format
	switch req.Format {
	case models.JwtVc, models.VcSdJwt, models.LdpVc:
	default:
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Unsupported credential format")
		return
	}

	// Get session ID from token claims
	claims, _ := token.Claims.(jwt.MapClaims)
	sessionID, _ := claims["session_id"].(string)

	// Look up the verified Veriff session bound to this token
	var session veriff.Session
	var found bool
	if sessionID != "" {
		session, found = s.sessions.Get(sessionID)
	}
	if !found {
		common.WriteError(w, r, http.StatusBadRequest, "no_session", "No verified identity session found")
		return
	}

	validation := veriff.ValidateSession(session)
	if !validation.IsValid {
		common.WriteError(w, r, http.StatusBadRequest, "validation_failed", validation.Reason)
		return
	}

	resp := credential.Build(session, validation, req.Types, string(req.Format))

	credentialsIssued.Add(r.Context(), 1, metric.WithAttributes(attribute.String("format", string(req.Format))))
	qualityTierGauge.Add(r.Context(), 1, metric.WithAttributes(attribute.String("tier", validation.QualityLevel)))

	log.Ctx(r.Context()).Info().Str("credential_id", resp.Credential.Id).Msg("credential issued")
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleVeriffWebhook(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	// Verify HMAC-SHA256 signature when webhook secret is configured
	if s.webhookSecret != "" {
		sig := r.Header.Get("X-HMAC-Signature")
		if sig == "" {
			common.WriteError(w, r, http.StatusUnauthorized, "missing_signature", "Missing X-HMAC-Signature header")
			return
		}
		mac := hmac.New(sha256.New, []byte(s.webhookSecret))
		mac.Write(body)
		expected := hex.EncodeToString(mac.Sum(nil))
		if !hmac.Equal([]byte(sig), []byte(expected)) {
			common.WriteError(w, r, http.StatusUnauthorized, "invalid_signature", "Webhook signature verification failed")
			return
		}
	}

	var session veriff.Session
	if err := json.Unmarshal(body, &session); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	if session.SessionID == "" {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "session_id is required")
		return
	}

	logger := log.Ctx(r.Context())
	webhooksReceived.Add(r.Context(), 1, metric.WithAttributes(attribute.String("status", session.Status)))
	logger.Info().Str("session_id", session.SessionID).Str("status", session.Status).Msg("webhook received")

	if session.Status != "approved" {
		w.WriteHeader(http.StatusAccepted)
		return
	}

	validation := veriff.ValidateSession(session)
	if !validation.IsValid {
		logger.Warn().Str("session_id", session.SessionID).Str("reason", validation.Reason).Msg("session failed quality validation")
		w.WriteHeader(http.StatusAccepted)
		return
	}

	s.sessions.Put(session)
	webhooksStored.Add(r.Context(), 1, metric.WithAttributes(attribute.String("quality_level", validation.QualityLevel)))
	logger.Info().
		Str("session_id", session.SessionID).
		Str("quality_level", validation.QualityLevel).
		Float64("confidence", validation.Confidence).
		Msg("session stored")
	w.WriteHeader(http.StatusOK)
}
