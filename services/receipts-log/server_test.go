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

var testCfg = common.ServerConfig{Name: "receipts-test", Version: "0.0.1", Port: "0"}

func TestHealthCheck(t *testing.T) {
	s := NewServer(testCfg)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), `"status":"ok"`)
}

func TestSubmitHash_Success(t *testing.T) {
	s := NewServer(testCfg)
	body, _ := json.Marshal(models.ReceiptHashRequest{ReceiptHash: "urn:sha256:abc123"})
	req := httptest.NewRequest(http.MethodPost, "/receipts/hash", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, true, resp["accepted"])
	assert.Equal(t, "urn:sha256:abc123", resp["hash"])
	assert.Equal(t, false, resp["anchored"])
}

func TestSubmitHash_InvalidJSON(t *testing.T) {
	s := NewServer(testCfg)
	req := httptest.NewRequest(http.MethodPost, "/receipts/hash", bytes.NewReader([]byte("bad")))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "invalid_request")
}

func TestSignedTreeHead(t *testing.T) {
	s := NewServer(testCfg)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/log/sth", nil))

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, float64(0), resp["treeSize"])
}

func TestInclusionProof(t *testing.T) {
	s := NewServer(testCfg)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/log/proof", nil))

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, false, resp["included"])
}

func TestRouteNotFound(t *testing.T) {
	s := NewServer(testCfg)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/nonexistent", nil))
	assert.Equal(t, http.StatusNotFound, w.Code)
}
