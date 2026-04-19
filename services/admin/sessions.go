package main

import (
	"encoding/json"
	"io"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

// handleListSessions aggregates active sessions from relay and verifier.
func (s *Server) handleListSessions(w http.ResponseWriter, r *http.Request) {
	result := map[string]interface{}{}

	// Fetch relay sessions
	if relay, err := s.fetchSessionStats(r, s.relayURL+"/internal/sessions"); err == nil {
		result["relay"] = relay
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("failed to fetch relay sessions")
		result["relay"] = map[string]interface{}{"error": "unavailable"}
	}

	// Fetch verifier sessions
	if verifier, err := s.fetchSessionStats(r, s.verifierURL+"/internal/sessions"); err == nil {
		result["verifier"] = verifier
	} else {
		log.Ctx(r.Context()).Warn().Err(err).Msg("failed to fetch verifier sessions")
		result["verifier"] = map[string]interface{}{"error": "unavailable"}
	}

	common.WriteJSON(w, r, http.StatusOK, result)
}

// handleForceExpireSession force-expires a session on the specified service.
func (s *Server) handleForceExpireSession(w http.ResponseWriter, r *http.Request) {
	service := chi.URLParam(r, "service")
	sessionID := chi.URLParam(r, "id")

	var baseURL string
	switch service {
	case "relay":
		baseURL = s.relayURL
	case "verifier":
		baseURL = s.verifierURL
	default:
		common.WriteError(w, r, http.StatusBadRequest, "invalid_service", "Service must be 'relay' or 'verifier'")
		return
	}

	req, err := http.NewRequestWithContext(r.Context(), http.MethodDelete, baseURL+"/internal/sessions/"+sessionID, nil)
	if err != nil {
		common.WriteError(w, r, http.StatusInternalServerError, "request_failed", err.Error())
		return
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach service")
		common.WriteError(w, r, http.StatusBadGateway, "service_error", "Failed to reach "+service)
		return
	}
	_ = resp.Body.Close()

	common.AuditLog(r, "session.force_expired", service+":"+sessionID, "success")
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) fetchSessionStats(r *http.Request, url string) (interface{}, error) {
	resp, err := s.proxyGet(r, url)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var result interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, err
	}
	return result, nil
}
