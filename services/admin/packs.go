package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

// handleCreatePack proxies pack creation to the registry, with audit logging.
func (s *Server) handleCreatePack(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	resp, err := s.proxyBody(r, http.MethodPost, s.registryURL+"/internal/packs", body)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to reach registry")
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusCreated {
		common.AuditLog(r, "pack.created", extractPackID(body), "success")
	}
	proxyResponse(w, resp)
}

// handleUpdatePack proxies pack update to the registry, with audit logging.
func (s *Server) handleUpdatePack(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	resp, err := s.proxyBody(r, http.MethodPut, s.registryURL+"/internal/packs/"+packID, body)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to reach registry")
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusOK {
		common.AuditLog(r, "pack.updated", packID, "success")
	}
	proxyResponse(w, resp)
}

// handlePatchPackStatus proxies pack enable/disable to the registry.
func (s *Server) handlePatchPackStatus(w http.ResponseWriter, r *http.Request) {
	packID := chi.URLParam(r, "id")
	body, err := io.ReadAll(r.Body)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_request", "Failed to read request body")
		return
	}

	resp, err := s.proxyBody(r, http.MethodPatch, s.registryURL+"/internal/packs/"+packID+"/status", body)
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach registry")
		common.WriteError(w, r, http.StatusBadGateway, "registry_error", "Failed to reach registry")
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusOK {
		common.AuditLog(r, "pack.status_changed", packID, "success")
	}
	proxyResponse(w, resp)
}

// proxyBody makes a request with a body to an internal service.
func (s *Server) proxyBody(r *http.Request, method, url string, body []byte) (*http.Response, error) {
	req, err := http.NewRequestWithContext(r.Context(), method, url, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	return s.httpClient.Do(req)
}

// extractPackID is a best-effort extraction of pack ID from JSON body for audit logs.
func extractPackID(body []byte) string {
	// Simple approach: look for "id" field. Don't import encoding/json just for this.
	type idOnly struct {
		ID string `json:"id"`
	}
	var p idOnly
	if err := json.Unmarshal(body, &p); err == nil && p.ID != "" {
		return p.ID
	}
	return "unknown"
}
