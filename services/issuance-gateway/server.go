package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"io"
	"net/http"
	"os"
	"path/filepath"

	kms "cloud.google.com/go/kms/apiv1"
	"github.com/go-chi/chi/v5"
	"github.com/golang-jwt/jwt/v5"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/credential"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/oauth"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/statuslist"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
)

// ServerConfig holds injectable dependencies for the issuance gateway.
type ServerConfig struct {
	Common        common.ServerConfig
	SigningKey    *rsa.PrivateKey   // RSA key for OAuth2 access tokens
	IssuerSigner  credential.Signer // SD-JWT VC signing (FileSigner for dev, KMSSigner for prod)
	Sessions      veriff.SessionStore
	WebhookSecret string // HMAC-SHA256 secret for Veriff webhook signature verification
}

// DefaultServerConfig creates a config with generated keys and in-memory store.
// The issuer EC key is persisted to disk so it survives restarts — otherwise
// every restart generates a new key, invalidating all previously issued credentials.
func DefaultServerConfig() ServerConfig {
	rsaKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to generate RSA key")
	}

	kid := os.Getenv("CACHET_ISSUER_KEY_ID")
	if kid == "" {
		kid = "did:veriff:production#key-1"
	}

	var signer credential.Signer
	if kmsKeyName := os.Getenv("CACHET_KMS_KEY_NAME"); kmsKeyName != "" {
		client, err := kms.NewKeyManagementClient(context.Background())
		if err != nil {
			log.Fatal().Err(err).Msg("failed to create KMS client")
		}
		signer = credential.NewKMSSigner(client, kmsKeyName, kid)
		log.Info().Str("key", kmsKeyName).Msg("using GCP KMS for credential signing")
	} else {
		ecKey := loadOrGenerateIssuerKey()
		signer = credential.NewFileSigner(ecKey, kid)
		log.Info().Msg("using file-based key for credential signing")
	}

	return ServerConfig{
		Common: common.ServerConfig{
			Name:    "issuance-gateway",
			Version: "0.1.0",
			Port:    "8090",
		},
		SigningKey:   rsaKey,
		IssuerSigner: signer,
		Sessions:     veriff.NewInMemoryStore(),
	}
}

// loadOrGenerateIssuerKey loads the issuer EC key from ISSUER_KEY_FILE (or a
// default path under DEVENV_STATE), generating and persisting a new one if
// the file doesn't exist yet. This ensures credentials remain verifiable
// across service restarts.
func loadOrGenerateIssuerKey() *ecdsa.PrivateKey {
	keyPath := os.Getenv("ISSUER_KEY_FILE")
	if keyPath == "" {
		if state := os.Getenv("DEVENV_STATE"); state != "" {
			keyPath = filepath.Join(state, "issuer-key.pem")
		}
	}

	keyPath = filepath.Clean(keyPath)

	// No persistence path configured — generate ephemeral key (CI, tests)
	if keyPath == "." {
		key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if err != nil {
			log.Fatal().Err(err).Msg("failed to generate ES256 issuer key")
		}
		log.Warn().Msg("no ISSUER_KEY_FILE or DEVENV_STATE set — using ephemeral issuer key")
		return key
	}

	// Try loading existing key
	if data, err := os.ReadFile(keyPath); err == nil {
		block, _ := pem.Decode(data)
		if block != nil {
			key, err := x509.ParseECPrivateKey(block.Bytes)
			if err == nil {
				log.Info().Str("path", keyPath).Msg("loaded issuer key from disk")
				return key
			}
			log.Warn().Err(err).Msg("failed to parse issuer key PEM — generating new key")
		}
	}

	// Generate new key and persist
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to generate ES256 issuer key")
	}

	der, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to marshal issuer key")
	}
	pemBlock := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: der})

	if err := os.MkdirAll(filepath.Dir(keyPath), 0o700); err != nil {
		log.Error().Err(err).Msg("failed to create directory for issuer key")
	} else if err := os.WriteFile(keyPath, pemBlock, 0o600); err != nil {
		log.Error().Err(err).Msg("failed to persist issuer key — will be ephemeral")
	} else {
		log.Info().Str("path", keyPath).Msg("generated and persisted new issuer key")
	}

	return key
}

type Server struct {
	router          *chi.Mux
	signingKey      *rsa.PrivateKey
	issuerSigner    credential.Signer
	sessions        veriff.SessionStore
	webhookSecret   string
	statusListStore *statuslist.Store
}

func NewServer() *Server {
	return NewServerWithConfig(DefaultServerConfig())
}

func NewServerWithConfig(cfg ServerConfig) *Server {
	slStore := statuslist.NewStore()
	s := &Server{
		router:          common.NewRouter(cfg.Common),
		signingKey:      cfg.SigningKey,
		issuerSigner:    cfg.IssuerSigner,
		sessions:        cfg.Sessions,
		webhookSecret:   cfg.WebhookSecret,
		statusListStore: slStore,
	}

	s.router.Post("/oauth/token", s.handleOAuthToken)
	s.router.Post("/credential", s.handleCredentialIssuance)
	s.router.Get("/status/{listId}", s.handleGetStatusList)
	s.router.Get("/status/{listId}/info", s.handleStatusListInfo)
	s.router.Post("/status/{listId}/revoke", s.handleRevoke)
	s.router.Post("/webhooks/veriff", s.handleVeriffWebhook)
	s.router.Get("/.well-known/jwks.json", s.handleJWKS)

	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

// handleJWKS returns the issuer's public key as a JWKS document.
// Used by the verifier to discover the issuer key for SD-JWT verification.
func (s *Server) handleJWKS(w http.ResponseWriter, r *http.Request) {
	if s.issuerSigner == nil {
		common.WriteError(w, r, http.StatusNotFound, "not_configured", "No issuer key configured")
		return
	}
	pub, err := s.issuerSigner.PublicKey()
	if err != nil {
		common.WriteError(w, r, http.StatusInternalServerError, "key_error", "Failed to retrieve public key")
		return
	}
	jwk := map[string]interface{}{
		"kty": "EC",
		"crv": "P-256",
		"x":   base64URLEncode(pub.X.Bytes()),
		"y":   base64URLEncode(pub.Y.Bytes()),
		"kid": s.issuerSigner.KeyID(),
		"use": "sig",
	}
	common.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"keys": []interface{}{jwk},
	})
}

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

	credentialsIssued.Add(r.Context(), 1, metric.WithAttributes(attribute.String("format", string(req.Format))))
	qualityTierGauge.Add(r.Context(), 1, metric.WithAttributes(attribute.String("tier", validation.QualityLevel)))

	// SD-JWT format: return signed SD-JWT string
	if req.Format == models.VcSdJwt && s.issuerSigner != nil {
		// Extract holder JWK from proof field for holder binding (cnf)
		var holderJWK map[string]interface{}
		if req.Proof != nil {
			if jwk, ok := (*req.Proof)["jwk"]; ok {
				if jwkMap, ok := jwk.(map[string]interface{}); ok {
					holderJWK = jwkMap
				}
			}
		}

		// Allocate status list index for revocation support
		statusIndex, err := s.statusListStore.AllocateIndex("1")
		if err != nil {
			log.Ctx(r.Context()).Error().Err(err).Msg("status list index allocation failed")
			common.WriteError(w, r, http.StatusInternalServerError, "server_error", "Failed to allocate credential status")
			return
		}

		sdJWT, err := credential.BuildSDJWTCredential(session, validation, req.Types, s.issuerSigner, holderJWK, statusIndex)
		if err != nil {
			log.Ctx(r.Context()).Error().Err(err).Msg("SD-JWT credential building failed")
			common.WriteError(w, r, http.StatusInternalServerError, "server_error", "Failed to build credential")
			return
		}
		log.Ctx(r.Context()).Info().Str("format", "vc+sd-jwt").Msg("SD-JWT credential issued")
		common.WriteJSON(w, r, http.StatusOK, map[string]string{
			"credential": sdJWT,
			"format":     "vc+sd-jwt",
		})
		return
	}

	// Legacy JSON format
	resp := credential.Build(session, validation, req.Types, string(req.Format))
	log.Ctx(r.Context()).Info().Str("credential_id", resp.Credential.Id).Msg("credential issued")
	common.WriteJSON(w, r, http.StatusOK, resp)
}

func (s *Server) handleVeriffWebhook(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	// HMAC-SHA256 signature verification — fail-closed: reject if secret not configured
	if s.webhookSecret == "" {
		log.Ctx(r.Context()).Error().Msg("webhook secret not configured — rejecting request")
		common.WriteError(w, r, http.StatusInternalServerError, "server_misconfigured", "Webhook verification unavailable")
		return
	}
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

func (s *Server) handleGetStatusList(w http.ResponseWriter, r *http.Request) {
	listID := chi.URLParam(r, "listId")
	encoded, err := s.statusListStore.GetEncoded(listID)
	if err != nil {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Status list not found")
		return
	}
	purpose, _ := s.statusListStore.GetPurpose(listID)

	w.Header().Set("Cache-Control", "max-age=300")
	common.WriteJSON(w, r, http.StatusOK, map[string]string{
		"id":          "https://cachet.id/status/" + listID,
		"type":        "BitstringStatusListCredential",
		"purpose":     purpose,
		"encodedList": encoded,
	})
}

func (s *Server) handleStatusListInfo(w http.ResponseWriter, r *http.Request) {
	listID := chi.URLParam(r, "listId")
	info, err := s.statusListStore.Info(listID)
	if err != nil {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Status list not found")
		return
	}
	common.WriteJSON(w, r, http.StatusOK, info)
}

func (s *Server) handleRevoke(w http.ResponseWriter, r *http.Request) {
	listID := chi.URLParam(r, "listId")

	var req struct {
		Index int `json:"index"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	if err := s.statusListStore.Revoke(listID, req.Index); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "revoke_failed", err.Error())
		return
	}

	log.Ctx(r.Context()).Info().Str("list_id", listID).Int("index", req.Index).Msg("credential revoked")
	w.WriteHeader(http.StatusOK)
}

func base64URLEncode(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}
