package main

import (
	"context"
	"crypto/ecdsa"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/verifier/internal/eval"
	"github.com/cachet-id/cachet/services/verifier/internal/identity"
	"github.com/cachet-id/cachet/services/verifier/internal/jwe"
	"github.com/cachet-id/cachet/services/verifier/internal/pack"
	"github.com/cachet-id/cachet/services/verifier/internal/session"
	"github.com/cachet-id/cachet/services/verifier/internal/statuslist"
)

// VerifierConfig holds injectable dependencies for the verifier.
type VerifierConfig struct {
	Common         common.ServerConfig
	RegistryURL    string
	VerifierDID    string           // this verifier's DID for audience binding
	DIDResolver    eval.DIDResolver // resolves issuer DIDs to public keys
	IdentitySigner *identity.Signer // signs Request Objects (nil = unsigned)
}

type Server struct {
	router         *chi.Mux
	packClient     *pack.Client
	didResolver    eval.DIDResolver
	sessions       *session.Manager
	verifierDID    string
	statusChecker  *statuslist.Checker
	identitySigner *identity.Signer
}

func NewServer(cfg common.ServerConfig, registryURL string) *Server {
	return NewServerWithConfig(VerifierConfig{
		Common:      cfg,
		RegistryURL: registryURL,
	})
}

func NewServerWithConfig(cfg VerifierConfig) *Server {
	s := &Server{
		router:         common.NewRouter(cfg.Common),
		packClient:     pack.NewClient(cfg.RegistryURL),
		didResolver:    cfg.DIDResolver,
		sessions:       session.NewManager(5 * time.Minute),
		verifierDID:    cfg.VerifierDID,
		statusChecker:  statuslist.NewChecker(),
		identitySigner: cfg.IdentitySigner,
	}
	s.router.Get("/packs", s.handleListPacks)
	s.router.Post("/sessions", s.handleCreateSession)
	s.router.Post("/presentations/verify", s.handleVerifyPresentation)
	s.router.Get("/.well-known/did.json", s.handleDIDDocument)

	// Internal endpoints for admin service
	s.router.Get("/internal/sessions", s.handleListSessions)
	s.router.Delete("/internal/sessions/{id}", s.handleForceExpireSession)

	return s
}

// WithIssuerKey configures the verifier with a known issuer public key for DID resolution.
func WithIssuerKey(did string, key *ecdsa.PublicKey) func(*VerifierConfig) {
	return func(cfg *VerifierConfig) {
		cfg.DIDResolver = eval.NewStaticDIDResolver(map[string]*ecdsa.PublicKey{did: key})
	}
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	summaries, err := s.packClient.ListSummary()
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to fetch packs from registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to fetch packs from registry")
		return
	}
	log.Ctx(r.Context()).Info().Int("pack_count", len(summaries)).Msg("listing packs")
	common.WriteJSON(w, r, http.StatusOK, summaries)
}

// createSessionRequest is the optional body for POST /sessions.
type createSessionRequest struct {
	PackID     string   `json:"packId,omitempty"`
	Question   string   `json:"question,omitempty"`
	Predicates []string `json:"predicates,omitempty"`
}

// handleCreateSession creates a verification session with a fresh nonce.
// If an identity signer is configured, includes a signed Request Object.
func (s *Server) handleCreateSession(w http.ResponseWriter, r *http.Request) {
	// Parse optional request body (pack metadata for the signed request object)
	var reqBody createSessionRequest
	if r.Body != nil && r.ContentLength > 0 {
		_ = json.NewDecoder(r.Body).Decode(&reqBody)
	}

	sess := s.sessions.Create(s.verifierDID)
	sessionsCreated.Add(r.Context(), 1)
	log.Ctx(r.Context()).Info().Str("session_id", sess.ID).Msg("verification session created")

	response := map[string]interface{}{
		"sessionId":       sess.ID,
		"nonce":           sess.Nonce,
		"verifierDid":     sess.VerifierDID,
		"ephemeralPubKey": sess.EphemeralPubKey,
	}

	// Sign Request Object if identity signer is available
	if s.identitySigner != nil {
		signedJWT, err := s.identitySigner.SignRequestObject(identity.RequestObjectClaims{
			Nonce:      sess.Nonce,
			State:      sess.ID,
			PackID:     reqBody.PackID,
			Question:   reqBody.Question,
			Predicates: reqBody.Predicates,
		})
		if err != nil {
			log.Ctx(r.Context()).Error().Err(err).Msg("failed to sign request object")
		} else {
			response["requestObject"] = signedJWT
		}
	}

	common.WriteJSON(w, r, http.StatusOK, response)
}

// handleDIDDocument serves the verifier's DID document with its public key.
func (s *Server) handleDIDDocument(w http.ResponseWriter, r *http.Request) {
	if s.identitySigner == nil {
		common.WriteError(w, r, http.StatusNotFound, "not_configured", "No identity key configured")
		return
	}
	doc := map[string]interface{}{
		"@context": []string{"https://www.w3.org/ns/did/v1"},
		"id":       s.verifierDID,
		"verificationMethod": []map[string]interface{}{
			{
				"id":           s.verifierDID + "#key-1",
				"type":         "JsonWebKey2020",
				"controller":   s.verifierDID,
				"publicKeyJwk": s.identitySigner.PublicKeyJWK(),
			},
		},
		"authentication": []string{s.verifierDID + "#key-1"},
	}
	common.WriteJSON(w, r, http.StatusOK, doc)
}

// verifyRequest extends the generated VerifyRequest with SD-JWT credentials and session binding.
type verifyRequest struct {
	PolicyId  string `json:"policyId"`
	SessionId string `json:"sessionId,omitempty"` // binds to a nonce-bearing session
	Bundle    struct {
		Credentials []models.VerifiableCredential `json:"credentials"`
	} `json:"bundle"`
	SDJWTCredentials []string `json:"sdJwtCredentials,omitempty"`
}

func (s *Server) handleVerifyPresentation(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	var req verifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	log.Ctx(r.Context()).Info().Str("policy_id", req.PolicyId).Msg("verifying presentation")
	packAttr := attribute.String("pack_id", req.PolicyId)

	// Fetch pack definition from registry
	packsRequested.Add(r.Context(), 1, metric.WithAttributes(packAttr))
	packDef, err := s.packClient.GetPack(req.PolicyId)
	if err != nil {
		log.Ctx(r.Context()).Warn().Err(err).Str("policy_id", req.PolicyId).Msg("pack not found")
		recordVerification(r.Context(), start, req.PolicyId, "error")
		common.WriteError(w, r, http.StatusBadRequest, "unknown_pack", "Pack not found: "+req.PolicyId)
		return
	}

	var results []models.PredicateResult
	var summary models.VerificationSummary

	// SD-JWT path: cryptographically verify credentials, then evaluate
	if len(req.SDJWTCredentials) > 0 && s.didResolver != nil {
		// Consume session nonce if session ID provided
		var expectedNonce, expectedAud string
		var sess *session.Session
		if req.SessionId != "" {
			var err error
			sess, err = s.sessions.Consume(req.SessionId)
			if err != nil {
				log.Ctx(r.Context()).Warn().Err(err).Str("session_id", req.SessionId).Msg("session validation failed")
				recordVerification(r.Context(), start, req.PolicyId, "error")
				common.WriteError(w, r, http.StatusBadRequest, "invalid_session", "Session validation failed: "+err.Error())
				return
			}
			expectedNonce = sess.Nonce
			expectedAud = sess.VerifierDID
		}

		var verifiedClaims []*eval.VerifiedClaims
		for i, credential := range req.SDJWTCredentials {
			// Attempt JWE decryption if session has an ephemeral key
			sdJWT := credential
			if sess != nil && sess.EphemeralPrivateKey() != nil && jwe.IsJWE(credential) {
				decrypted, decErr := jwe.Decrypt(credential, sess.EphemeralPrivateKey())
				if decErr != nil {
					log.Ctx(r.Context()).Warn().Err(decErr).Int("credential_index", i).Msg("JWE decryption failed")
					recordVerification(r.Context(), start, req.PolicyId, "error")
					common.WriteError(w, r, http.StatusBadRequest, "decryption_failed",
						fmt.Sprintf("JWE decryption failed for credential %d: %v", i, decErr))
					return
				}
				sdJWT = string(decrypted)
				log.Ctx(r.Context()).Info().Int("credential_index", i).Msg("JWE decrypted successfully")
			}

			vc, err := eval.VerifySDJWT(sdJWT, s.didResolver)
			if err != nil {
				log.Ctx(r.Context()).Warn().Err(err).Int("credential_index", i).Msg("SD-JWT verification failed")
				recordVerification(r.Context(), start, req.PolicyId, "error")
				common.WriteError(w, r, http.StatusBadRequest, "verification_failed", "Credential verification failed: "+err.Error())
				return
			}

			// Validate nonce and audience from KB-JWT if session was provided
			if expectedNonce != "" && vc.HolderBound {
				if vc.KBJWTNonce != expectedNonce {
					recordVerification(r.Context(), start, req.PolicyId, "error")
					common.WriteError(w, r, http.StatusBadRequest, "nonce_mismatch", "KB-JWT nonce does not match session")
					return
				}
				if expectedAud != "" && vc.KBJWTAud != expectedAud {
					recordVerification(r.Context(), start, req.PolicyId, "error")
					common.WriteError(w, r, http.StatusBadRequest, "audience_mismatch", "KB-JWT audience does not match verifier")
					return
				}
			}

			// Check revocation via StatusList2021
			if vc.Status != nil {
				slURL, _ := vc.Status["statusListCredential"].(string)
				slIdxStr, _ := vc.Status["statusListIndex"].(string)
				if slURL != "" && slIdxStr != "" {
					var slIdx int
					if _, err := fmt.Sscanf(slIdxStr, "%d", &slIdx); err != nil {
						log.Ctx(r.Context()).Warn().Err(err).Str("index", slIdxStr).Msg("invalid statusListIndex")
					}
					revoked, err := s.statusChecker.IsRevoked(slURL, slIdx)
					if err != nil {
						log.Ctx(r.Context()).Warn().Err(err).Str("url", slURL).Msg("revocation check failed")
					} else if revoked {
						recordVerification(r.Context(), start, req.PolicyId, "error")
						common.WriteError(w, r, http.StatusBadRequest, "credential_revoked", "Credential has been revoked")
						return
					}
				}
			}

			verifiedClaims = append(verifiedClaims, vc)
		}
		results, summary = eval.EvaluateWithVerifiedClaims(packDef, verifiedClaims)
	} else {
		// Legacy path: no cryptographic verification (backward compatible)
		results, summary = eval.Evaluate(packDef, req.Bundle.Credentials)
	}

	// Check freshness (works with both paths)
	freshness := eval.CheckFreshness(req.Bundle.Credentials)

	// Build satisfied predicate IDs list
	var satisfied []string
	for _, res := range results {
		if res.Status == models.Satisfied {
			satisfied = append(satisfied, res.PredicateId)
		}
	}

	// Determine cachet
	cachet := ""
	if summary.CachetGranted {
		cachet = packDef.Badge.Label
	}

	status := "fail"
	if summary.CachetGranted {
		status = "pass"
	}
	recordVerification(r.Context(), start, req.PolicyId, status)

	resp := models.VerifyResponse{
		Cachet:           cachet,
		Predicates:       satisfied,
		Freshness:        freshness,
		PredicateResults: results,
		Summary:          summary,
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}

// handleListSessions returns active verifier sessions for admin visibility.
func (s *Server) handleListSessions(w http.ResponseWriter, r *http.Request) {
	sessions := s.sessions.List()
	common.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"active":   len(sessions),
		"sessions": sessions,
	})
}

// handleForceExpireSession removes a verifier session immediately.
func (s *Server) handleForceExpireSession(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	s.sessions.ForceExpire(id)
	w.WriteHeader(http.StatusNoContent)
}

// recordVerification records both the counter and duration histogram for a verification attempt.
func recordVerification(ctx context.Context, start time.Time, packID, status string) {
	attrs := metric.WithAttributes(
		attribute.String("pack_id", packID),
		attribute.String("status", status),
	)
	verificationsTotal.Add(ctx, 1, attrs)
	verificationDuration.Record(ctx, time.Since(start).Seconds(), attrs)
}
