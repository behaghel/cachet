package main

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/golang-jwt/jwt/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/credential"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/oauth"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// ServerConfig holds injectable dependencies for the issuance gateway.
type ServerConfig struct {
	Common     common.ServerConfig
	SigningKey *rsa.PrivateKey
	Sessions   veriff.SessionStore
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
	router     *chi.Mux
	signingKey *rsa.PrivateKey
	sessions   veriff.SessionStore
}

func NewServer() *Server {
	return NewServerWithConfig(DefaultServerConfig())
}

func NewServerWithConfig(cfg ServerConfig) *Server {
	s := &Server{
		router:     common.NewRouter(cfg.Common),
		signingKey: cfg.SigningKey,
		sessions:   cfg.Sessions,
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

	if grantType != "client_credentials" {
		common.WriteError(w, r, http.StatusBadRequest, "unsupported_grant_type", "Only client_credentials is supported")
		return
	}
	if clientID == "" {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "client_id is required")
		return
	}

	resp, err := oauth.IssueToken(s.signingKey, clientID, scope)
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

	var req struct {
		Format string   `json:"format"`
		Types  []string `json:"types"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	// Validate format
	switch req.Format {
	case "jwt_vc", "vc+sd-jwt", "ldp_vc":
	default:
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Unsupported credential format")
		return
	}

	// Get session ID from token claims
	claims, _ := token.Claims.(jwt.MapClaims)
	sessionID, _ := claims["session_id"].(string)

	// Find a verified session — use session_id from token if available, else first approved
	var session veriff.Session
	var found bool
	if sessionID != "" {
		session, found = s.sessions.Get(sessionID)
	}
	if !found {
		// Fallback: this is temporary until session binding is fully wired
		// TODO: remove fallback once mobile sends session_id in token request
		session, found = s.findFirstApprovedSession()
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

	resp := credential.Build(session, validation, req.Types, req.Format)
	log.Ctx(r.Context()).Info().Str("credential_id", resp.Credential.ID).Msg("credential issued")
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleVeriffWebhook(w http.ResponseWriter, r *http.Request) {
	// TODO: add HMAC-SHA256 signature verification once webhook secret is configured
	var session veriff.Session
	if err := json.NewDecoder(r.Body).Decode(&session); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	if session.SessionID == "" {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "session_id is required")
		return
	}

	logger := log.Ctx(r.Context())
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
	logger.Info().
		Str("session_id", session.SessionID).
		Str("quality_level", validation.QualityLevel).
		Float64("confidence", validation.Confidence).
		Msg("session stored")
	w.WriteHeader(http.StatusOK)
}

// findFirstApprovedSession is a temporary fallback until session binding is wired.
func (s *Server) findFirstApprovedSession() (veriff.Session, bool) {
	store, ok := s.sessions.(*veriff.InMemoryStore)
	if !ok {
		return veriff.Session{}, false
	}
	return store.FindFirst(func(sess veriff.Session) bool {
		return sess.Status == "approved"
	})
}
