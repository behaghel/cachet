package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

// handleRevokeCredential revokes a credential by status list index.
func (s *Server) handleRevokeCredential(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, err := strconv.Atoi(indexStr)
	if err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_index", "Index must be an integer")
		return
	}

	var req struct {
		ListID string `json:"listId"`
		Reason string `json:"reason"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		common.WriteError(w, r, http.StatusBadRequest, "invalid_json", "Invalid request body")
		return
	}
	if req.ListID == "" {
		req.ListID = "1"
	}

	// Proxy to issuance gateway
	body := fmt.Sprintf(`{"index":%d}`, index)
	resp, err := s.proxyBody(r, http.MethodPost, s.issuanceURL+"/status/"+req.ListID+"/revoke", []byte(body))
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach issuance gateway")
		common.WriteError(w, r, http.StatusBadGateway, "issuance_error", "Failed to reach issuance gateway")
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusOK {
		resource := fmt.Sprintf("list:%s/index:%d", req.ListID, index)
		common.AuditLog(r, "credential.revoked", resource, "success")
	}

	common.WriteJSON(w, r, resp.StatusCode, map[string]interface{}{
		"revoked": resp.StatusCode == http.StatusOK,
		"index":   index,
		"listId":  req.ListID,
	})
}

// handleGetStatusListInfo returns status list statistics.
func (s *Server) handleGetStatusListInfo(w http.ResponseWriter, r *http.Request) {
	listID := chi.URLParam(r, "id")
	resp, err := s.proxyGet(r, s.issuanceURL+"/status/"+listID+"/info")
	if err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to reach issuance gateway")
		common.WriteError(w, r, http.StatusBadGateway, "issuance_error", "Failed to reach issuance gateway")
		return
	}
	defer func() { _ = resp.Body.Close() }()
	proxyResponse(w, resp)
}
