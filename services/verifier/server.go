package main

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/rs/zerolog/log"
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

func NewServer() *Server {
	s := &Server{
		router: chi.NewRouter(),
		packs: []Pack{
			{ID: "pack.childcare.readiness@0.1.0", Version: "0.1.0", Name: "Childcare Readiness"},
			{ID: "pack.safe.seller@0.1.0", Version: "0.1.0", Name: "Safe Seller"},
		},
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
	// Note: /healthz is reserved by Cloud Run infrastructure - use /health instead
	s.router.Get("/health", s.handleHealth) // Alternative health endpoint
	s.router.Get("/packs", s.handleListPacks)
	s.router.Post("/presentations/verify", s.handleVerifyPresentation)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Debug().Msg("Health check requested")
	w.WriteHeader(http.StatusOK)
	if _, err := w.Write([]byte("ok")); err != nil {
		log.Error().Err(err).Msg("Failed to write health check response")
	}
}

func (s *Server) handleListPacks(w http.ResponseWriter, r *http.Request) {
	log.Info().Int("pack_count", len(s.packs)).Msg("Listing packs")

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(s.packs); err != nil {
		log.Error().Err(err).Msg("Failed to encode packs response")
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
}

func (s *Server) handleVerifyPresentation(w http.ResponseWriter, r *http.Request) {
	var req VerifyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Error().Err(err).Msg("Failed to decode verify request")
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	log.Info().
		Str("policy_id", req.PolicyID).
		Msg("Verifying presentation")

	// Stub implementation
	resp := VerifyResponse{
		Badge:      "Demo Badge (stub)",
		Predicates: []string{"age.ge.18", "identity.verified"},
		Freshness:  "ok",
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		log.Error().Err(err).Msg("Failed to encode verify response")
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
}

func (s *Server) Start(addr string) error {
	log.Info().Str("addr", addr).Msg("Server starting")

	server := &http.Server{
		Addr:         addr,
		Handler:      s.router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	return server.ListenAndServe()
}
