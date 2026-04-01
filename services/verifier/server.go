package main

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/verifier/internal/eval"
	"github.com/cachet-id/cachet/services/verifier/internal/pack"
)

type Server struct {
	router     *chi.Mux
	packClient *pack.Client
}

func NewServer(cfg common.ServerConfig, registryURL string) *Server {
	s := &Server{
		router:     common.NewRouter(cfg),
		packClient: pack.NewClient(registryURL),
	}
	s.router.Get("/packs", s.handleListPacks)
	s.router.Post("/presentations/verify", s.handleVerifyPresentation)
	return s
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

func (s *Server) handleVerifyPresentation(w http.ResponseWriter, r *http.Request) {
	var req models.VerifyRequest
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

	// Evaluate predicates
	results, summary := eval.Evaluate(packDef, req.Bundle.Credentials)

	// Check freshness
	freshness := eval.CheckFreshness(req.Bundle.Credentials)

	// Build satisfied predicate IDs list
	var satisfied []string
	for _, res := range results {
		if res.Status == models.Satisfied {
			satisfied = append(satisfied, res.PredicateId)
		}
	}

	// Determine badge
	badge := ""
	if summary.BadgeGranted {
		badge = packDef.Badge.Label
	}

	resp := models.VerifyResponse{
		Badge:            badge,
		Predicates:       satisfied,
		Freshness:        freshness,
		PredicateResults: results,
		Summary:          summary,
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
