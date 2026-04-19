package main

import (
	"io"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"

	"github.com/cachet-id/cachet/services/common"
)

// Server is the relay — a stateless message broker for verification sessions.
// It stores request and response payloads as opaque bytes (never interprets them).
type Server struct {
	router   *chi.Mux
	sessions *SessionStore
}

// NewServer creates a relay server with 5-minute session TTL.
func NewServer(cfg common.ServerConfig) *Server {
	s := &Server{
		router:   common.NewRouter(cfg),
		sessions: NewSessionStore(5 * time.Minute),
	}
	s.router.Post("/sessions", s.handleCreateSession)
	s.router.Get("/sessions/{id}/request", s.handleGetRequest)
	s.router.Post("/sessions/{id}/response", s.handlePostResponse)
	s.router.Get("/sessions/{id}/response", s.handleGetResponse)

	// Internal endpoints for admin service
	s.router.Get("/internal/sessions", s.handleListSessions)
	s.router.Delete("/internal/sessions/{id}", s.handleForceExpireSession)

	return s
}

func (s *Server) Router() *chi.Mux { return s.router }

// handleCreateSession creates a new relay session.
// The request body is the signed Request Object (opaque to the relay).
func (s *Server) handleCreateSession(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	sess := s.sessions.Create(body)
	relaySessionsTotal.Add(r.Context(), 1, metric.WithAttributes(attribute.String("status", "created")))
	log.Ctx(r.Context()).Info().Str("session_id", sess.ID).Msg("relay session created")

	common.WriteJSON(w, r, http.StatusOK, map[string]string{
		"sessionId":   sess.ID,
		"requestUri":  "/sessions/" + sess.ID + "/request",
		"responseUri": "/sessions/" + sess.ID + "/response",
	})
}

// handleGetRequest returns the stored request payload for a session.
// Called by the holder after scanning the QR code.
func (s *Server) handleGetRequest(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	request, err := s.sessions.GetRequest(id)
	if err != nil {
		common.WriteError(w, r, http.StatusNotFound, "not_found", err.Error())
		return
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(request)
}

// handlePostResponse stores the holder's response (encrypted VP).
func (s *Server) handlePostResponse(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	if err := s.sessions.SetResponse(id, body); err != nil {
		common.WriteError(w, r, http.StatusNotFound, "not_found", err.Error())
		return
	}

	// Record holder response latency (time from session creation to response).
	if sess, latency, ok := s.sessions.ResponseLatency(id); ok {
		_ = sess
		relayResponseLatency.Record(r.Context(), latency.Seconds())
		relaySessionsTotal.Add(r.Context(), 1, metric.WithAttributes(attribute.String("status", "completed")))
	}

	log.Ctx(r.Context()).Info().Str("session_id", id).Msg("response posted")
	w.WriteHeader(http.StatusNoContent)
}

// handleGetResponse returns the response if available (verifier polls this).
// Returns 204 if the holder hasn't responded yet, 200 with body if they have.
func (s *Server) handleGetResponse(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	response, err := s.sessions.GetResponse(id)
	if err != nil {
		common.WriteError(w, r, http.StatusNotFound, "not_found", err.Error())
		return
	}

	if response == nil {
		w.WriteHeader(http.StatusNoContent) // holder hasn't responded yet
		return
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(response)
}

// handleListSessions returns active sessions for admin visibility.
func (s *Server) handleListSessions(w http.ResponseWriter, r *http.Request) {
	sessions := s.sessions.List()
	common.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"active":   len(sessions),
		"sessions": sessions,
	})
}

// handleForceExpireSession removes a session immediately.
func (s *Server) handleForceExpireSession(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	s.sessions.ForceExpire(id)
	w.WriteHeader(http.StatusNoContent)
}
