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
	packs  []models.CachPack
}

func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{
		router: common.NewRouter(cfg),
		packs: []models.CachPack{
			{Id: "pack.childcare.readiness@0.1.0", Version: "0.1.0", Name: "Childcare Readiness"},
			{Id: "pack.safe.seller@0.1.0", Version: "0.1.0", Name: "Safe Seller"},
		},
	}
	s.router.Get("/packs", s.handleListCachPacks)
	s.router.Post("/presentations/cache", s.handleCachePresentation)
	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handleListCachPacks(w http.ResponseWriter, r *http.Request) {
	log.Ctx(r.Context()).Info().Int("pack_count", len(s.packs)).Msg("listing cach'packs")
	common.WriteJSON(w, r, http.StatusOK, s.packs)
}

func (s *Server) handleCachePresentation(w http.ResponseWriter, r *http.Request) {
	var req models.CacheRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Invalid request body")
		return
	}

	log.Ctx(r.Context()).Info().Str("policy_id", req.PolicyId).Msg("caching presentation")

	// Stub implementation
	resp := models.CacheResponse{
		Cachet:     "Demo Cachet (stub)",
		Predicates: []string{"age.ge.18", "identity.verified"},
		Freshness:  models.CacheResponseFreshnessOk,
	}
	common.WriteJSON(w, r, http.StatusOK, resp)
}
