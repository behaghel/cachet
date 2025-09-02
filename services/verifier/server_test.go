package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewServer(t *testing.T) {
	server := NewServer()
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
	assert.Len(t, server.packs, 2)
}

func TestHealthCheck(t *testing.T) {
	server := NewServer()

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "ok", w.Body.String())
}

func TestListPacks(t *testing.T) {
	server := NewServer()

	req := httptest.NewRequest(http.MethodGet, "/packs", nil)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "application/json", w.Header().Get("Content-Type"))

	var packs []Pack
	err := json.Unmarshal(w.Body.Bytes(), &packs)
	require.NoError(t, err)

	assert.Len(t, packs, 2)
	assert.Equal(t, "pack.childcare.readiness@0.1.0", packs[0].ID)
	assert.Equal(t, "Childcare Readiness", packs[0].Name)
}

func TestVerifyPresentation_Success(t *testing.T) {
	server := NewServer()

	reqBody := VerifyRequest{
		PolicyID: "test.policy",
		Bundle:   map[string]interface{}{"test": "data"},
	}

	body, err := json.Marshal(reqBody)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "application/json", w.Header().Get("Content-Type"))

	var resp VerifyResponse
	err = json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.Equal(t, "Demo Badge (stub)", resp.Badge)
	assert.Contains(t, resp.Predicates, "age.ge.18")
	assert.Contains(t, resp.Predicates, "identity.verified")
	assert.Equal(t, "ok", resp.Freshness)
}

func TestVerifyPresentation_InvalidJSON(t *testing.T) {
	server := NewServer()

	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader([]byte("invalid json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "Invalid request body")
}

func TestRouteNotFound(t *testing.T) {
	server := NewServer()

	req := httptest.NewRequest(http.MethodGet, "/nonexistent", nil)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}
