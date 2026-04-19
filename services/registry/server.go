package main

import (
	"context"
	"fmt"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/registry/internal/pack"
)

const policyManifest = `id: policy.cachet.manifest
version: 0.1.0
issuedAt: 2025-08-31T00:00:00Z
signingDid: did:web:cachet.id#keys-1`

type Server struct {
	router    *chi.Mux
	packStore *pack.Store
}

func NewServer(cfg common.ServerConfig) *Server {
	packStore, err := pack.LoadFromFS(pack.EmbeddedPacksFS())
	if err != nil {
		log.Fatal().Err(err).Msg("failed to load pack definitions")
	}

	cfg.ReadinessChecks = append(cfg.ReadinessChecks, func(_ context.Context) error {
		if len(packStore.List()) == 0 {
			return fmt.Errorf("no packs loaded")
		}
		return nil
	})

	s := &Server{
		router:    common.NewRouter(cfg),
		packStore: packStore,
	}
	s.router.Get("/policy/manifest", s.handlePolicyManifest)
	s.router.Get("/registry/packs", s.handleListPacks)
	s.router.Get("/registry/packs/{packId}", s.handleGetPack)
	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

func (s *Server) handlePolicyManifest(w http.ResponseWriter, r *http.Request) {
	log.Ctx(r.Context()).Info().Msg("policy manifest requested")
	w.Header().Set("Content-Type", "text/yaml")
	if _, err := w.Write([]byte(policyManifest)); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("write failed")
	}
}

func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	packs := s.packStore.List()
	log.Ctx(r.Context()).Info().Int("pack_count", len(packs)).Msg("listing pack definitions")
	common.WriteJSON(w, r, http.StatusOK, packs)
}

func (s *Server) handleGetPack(w http.ResponseWriter, r *http.Request) {
	packId := chi.URLParam(r, "packId")
	p, ok := s.packStore.Get(packId)
	if !ok {
		common.WriteError(w, r, http.StatusNotFound, "not_found", "Pack not found: "+packId)
		return
	}
	log.Ctx(r.Context()).Info().Str("pack_id", packId).Msg("pack definition requested")
	common.WriteJSON(w, r, http.StatusOK, p)
}
