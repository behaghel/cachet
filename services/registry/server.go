package main

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/rs/zerolog/log"
)

const policyManifest = `id: policy.cachet.manifest
version: 0.1.0
issuedAt: 2025-08-31T00:00:00Z
signingDid: did:web:cachet.id#keys-1`

type Server struct {
	router *chi.Mux
}

func NewServer() *Server {
	s := &Server{
		router: chi.NewRouter(),
	}
	s.setupMiddleware()
	s.setupRoutes()
	return s
}

func (s *Server) setupMiddleware() {
	s.router.Use(middleware.RequestID)
	s.router.Use(middleware.RealIP)
	s.router.Use(middleware.Logger)
	s.router.Use(middleware.Recoverer)
}

func (s *Server) setupRoutes() {
	s.router.Get("/healthz", s.handleHealth)
	s.router.Get("/policy/manifest", s.handlePolicyManifest)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Debug().Msg("Health check requested")
	w.WriteHeader(http.StatusOK)
	if _, err := w.Write([]byte("ok")); err != nil {
		log.Error().Err(err).Msg("Failed to write health check response")
	}
}

func (s *Server) handlePolicyManifest(w http.ResponseWriter, r *http.Request) {
	log.Info().Msg("Policy manifest requested")
	w.Header().Set("Content-Type", "text/yaml")
	if _, err := w.Write([]byte(policyManifest)); err != nil {
		log.Error().Err(err).Msg("Failed to write policy manifest response")
	}
}

func (s *Server) Start(addr string) error {
	log.Info().Str("addr", addr).Msg("Registry server starting")

	server := &http.Server{
		Addr:         addr,
		Handler:      s.router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	return server.ListenAndServe()
}
