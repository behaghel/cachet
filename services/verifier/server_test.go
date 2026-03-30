package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var testCfg = common.ServerConfig{Name: "verifier-test", Version: "0.0.1", Port: "0"}

func TestNewServer(t *testing.T) {
	server := NewServer(testCfg)
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
	assert.Len(t, server.packs, 2)
}

func TestHealthCheck(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), `"status":"ok"`)
}

func TestListPacks(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/packs", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var packs []models.Pack
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &packs))
	assert.Len(t, packs, 2)
	assert.Equal(t, "pack.childcare.readiness@0.1.0", packs[0].Id)
	assert.Equal(t, "Childcare Readiness", packs[0].Name)
}

func TestVerifyPresentation_Success(t *testing.T) {
	server := NewServer(testCfg)
	reqBody := models.VerifyRequest{
		PolicyId: "test.policy",
		Bundle:   map[string]interface{}{"test": "data"},
	}
	body, _ := json.Marshal(reqBody)
	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body))
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp models.VerifyResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Demo Badge (stub)", resp.Badge)
	assert.Contains(t, resp.Predicates, "age.ge.18")
	assert.Equal(t, models.VerifyResponseFreshnessOk, resp.Freshness)
}

func TestVerifyPresentation_InvalidJSON(t *testing.T) {
	server := NewServer(testCfg)
	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader([]byte("bad")))
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRouteNotFound(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/nonexistent", nil))
	assert.Equal(t, http.StatusNotFound, w.Code)
}
