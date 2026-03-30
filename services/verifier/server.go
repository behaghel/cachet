package main

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
)

type Server struct {
	router *chi.Mux
	packs  []models.Pack
}

func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{
		router: common.NewRouter(cfg),
		packs: []models.Pack{
			{Id: "pack.childcare.readiness@0.1.0", Version: "0.1.0", Name: "Childcare Readiness"},
			{Id: "pack.safe.seller@0.1.0", Version: "0.1.0", Name: "Safe Seller"},
		},
	}
	s.router.Get("/packs", s.handleListPacks)
	s.router.Post("/presentations/verify", s.handleVerifyPresentation)
	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	log.Ctx(r.Context()).Info().Int("pack_count", len(s.packs)).Msg("listing packs")
	common.WriteJSON(w, r, http.StatusOK, s.packs)
}

func (s *Server) handleVerifyPresentation(w http.ResponseWriter, r *http.Request) {
	var req models.VerifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	log.Ctx(r.Context()).Info().Str("policy_id", req.PolicyId).Msg("verifying presentation")

	// Stub implementation
	resp := models.VerifyResponse{
		Badge:      "Demo Badge (stub)",
		Predicates: []string{"age.ge.18", "identity.verified"},
		Freshness:  models.VerifyResponseFreshnessOk,
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
