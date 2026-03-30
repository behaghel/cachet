package main

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

type Pack struct {
	ID      string `json:"id"`
	Version string `json:"version"`
	Name    string `json:"name"`
}

type VerifyRequest struct {
	PolicyID string      `json:"policyId"`
	Bundle   interface{} `json:"bundle"`
}

type VerifyResponse struct {
	Badge      string   `json:"badge"`
	Predicates []string `json:"predicates"`
	Freshness  string   `json:"freshness"`
}

type Server struct {
	router *chi.Mux
	packs  []Pack
}

func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{
		router: common.NewRouter(cfg),
		packs: []Pack{
			{ID: "pack.childcare.readiness@0.1.0", Version: "0.1.0", Name: "Childcare Readiness"},
			{ID: "pack.safe.seller@0.1.0", Version: "0.1.0", Name: "Safe Seller"},
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
	var req VerifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	log.Ctx(r.Context()).Info().Str("policy_id", req.PolicyID).Msg("verifying presentation")

	// Stub implementation
	resp := VerifyResponse{
		Badge:      "Demo Badge (stub)",
		Predicates: []string{"age.ge.18", "identity.verified"},
		Freshness:  "ok",
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
