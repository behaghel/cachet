package main

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

const policyManifest = `id: policy.cachet.manifest
version: 0.1.0
issuedAt: 2025-08-31T00:00:00Z
signingDid: did:web:cachet.id#keys-1`

type Server struct {
	router *chi.Mux
}

func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{router: common.NewRouter(cfg)}
	s.router.Get("/policy/manifest", s.handlePolicyManifest)
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
