package main

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cachet-id/cachet/services/common"
	"github.com/stretchr/testify/assert"
)

var testCfg = common.ServerConfig{Name: "registry-test", Version: "0.0.1", Port: "0"}

func TestNewServer(t *testing.T) {
	server := NewServer(testCfg)
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
}

func TestHealthCheck(t *testing.T) {
	server := NewServer(testCfg)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()

	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), `"status":"ok"`)
}

func TestPolicyManifest(t *testing.T) {
	server := NewServer(testCfg)

	req := httptest.NewRequest(http.MethodGet, "/policy/manifest", nil)
	w := httptest.NewRecorder()

	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "text/yaml", w.Header().Get("Content-Type"))
	assert.Contains(t, w.Body.String(), "id: policy.cachet.manifest")
	assert.Contains(t, w.Body.String(), "version: 0.1.0")
}

func TestRouteNotFound(t *testing.T) {
	server := NewServer(testCfg)

	req := httptest.NewRequest(http.MethodGet, "/nonexistent", nil)
	w := httptest.NewRecorder()

	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}
