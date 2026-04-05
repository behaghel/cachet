package main

import (
	"crypto/ecdsa"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/verifier/internal/eval"
	"github.com/cachet-id/cachet/services/verifier/internal/pack"
	"github.com/cachet-id/cachet/services/verifier/internal/session"
	"github.com/cachet-id/cachet/services/verifier/internal/statuslist"
)

// VerifierConfig holds injectable dependencies for the verifier.
type VerifierConfig struct {
	Common      common.ServerConfig
	RegistryURL string
	VerifierDID string           // this verifier's DID for audience binding
	DIDResolver eval.DIDResolver // resolves issuer DIDs to public keys
}

type Server struct {
	router        *chi.Mux
	packClient    *pack.Client
	didResolver   eval.DIDResolver
	sessions      *session.Manager
	verifierDID   string
	statusChecker *statuslist.Checker
}

func NewServer(cfg common.ServerConfig, registryURL string) *Server {
	return NewServerWithConfig(VerifierConfig{
		Common:      cfg,
		RegistryURL: registryURL,
	})
}

func NewServerWithConfig(cfg VerifierConfig) *Server {
	s := &Server{
		router:        common.NewRouter(cfg.Common),
		packClient:    pack.NewClient(cfg.RegistryURL),
		didResolver:   cfg.DIDResolver,
		sessions:      session.NewManager(5 * time.Minute),
		verifierDID:   cfg.VerifierDID,
		statusChecker: statuslist.NewChecker(),
	}
	s.router.Get("/packs", s.handleListPacks)
	s.router.Post("/sessions", s.handleCreateSession)
	s.router.Post("/presentations/verify", s.handleVerifyPresentation)
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

// handleCreateSession creates a verification session with a fresh nonce.
func (s *Server) handleCreateSession(w http.ResponseWriter, r *http.Request) {
	sess := s.sessions.Create(s.verifierDID)
	log.Ctx(r.Context()).Info().Str("session_id", sess.ID).Msg("verification session created")
	common.WriteJSON(w, r, http.StatusOK, sess)
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
	var req verifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	log.Ctx(r.Context()).Info().Str("policy_id", req.PolicyId).Msg("verifying presentation")

	// Fetch pack definition from registry
	packDef, err := s.packClient.GetPack(req.PolicyId)
	if err != nil {
		log.Ctx(r.Context()).Warn().Err(err).Str("policy_id", req.PolicyId).Msg("pack not found")
		common.WriteError(w, r, http.StatusBadRequest, "unknown_pack", "Pack not found: "+req.PolicyId)
		return
	}

	var results []models.PredicateResult
	var summary models.VerificationSummary

	// SD-JWT path: cryptographically verify credentials, then evaluate
	if len(req.SDJWTCredentials) > 0 && s.didResolver != nil {
		// Consume session nonce if session ID provided
		var expectedNonce, expectedAud string
		if req.SessionId != "" {
			sess, err := s.sessions.Consume(req.SessionId)
			if err != nil {
				log.Ctx(r.Context()).Warn().Err(err).Str("session_id", req.SessionId).Msg("session validation failed")
				common.WriteError(w, r, http.StatusBadRequest, "invalid_session", "Session validation failed: "+err.Error())
				return
			}
			expectedNonce = sess.Nonce
			expectedAud = sess.VerifierDID
		}

		var verifiedClaims []*eval.VerifiedClaims
		for i, sdJWT := range req.SDJWTCredentials {
			vc, err := eval.VerifySDJWT(sdJWT, s.didResolver)
			if err != nil {
				log.Ctx(r.Context()).Warn().Err(err).Int("credential_index", i).Msg("SD-JWT verification failed")
				common.WriteError(w, r, http.StatusBadRequest, "verification_failed", "Credential verification failed: "+err.Error())
				return
			}

			// Validate nonce and audience from KB-JWT if session was provided
			if expectedNonce != "" && vc.HolderBound {
				if vc.KBJWTNonce != expectedNonce {
					common.WriteError(w, r, http.StatusBadRequest, "nonce_mismatch", "KB-JWT nonce does not match session")
					return
				}
				if expectedAud != "" && vc.KBJWTAud != expectedAud {
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

	resp := models.VerifyResponse{
		Cachet:           cachet,
		Predicates:       satisfied,
		Freshness:        freshness,
		PredicateResults: results,
		Summary:          summary,
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
