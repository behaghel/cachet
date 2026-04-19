package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/cachet-id/cachet/services/common"
)

const testAPIKey = "test-secret-key"

func testServer(registryURL string) *Server {
	cfg := AdminConfig{
		Common: common.ServerConfig{
			Name:    "admin",
			Version: "0.1.0",
			Port:    "8091",
		},
		APIKey:      testAPIKey,
		RegistryURL: registryURL,
		IssuanceURL: "http://localhost:8090",
	}
	return NewServer(cfg)
}

func TestAuth_MissingKey(t *testing.T) {
	srv := testServer("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/admin/status", nil)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)

	var body map[string]string
	require.NoError(t, json.NewDecoder(rec.Body).Decode(&body))
	assert.Equal(t, "missing_api_key", body["error"])
}

func TestAuth_InvalidKey(t *testing.T) {
	srv := testServer("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/admin/status", nil)
	req.Header.Set("X-API-Key", "wrong-key")
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)

	var body map[string]string
	require.NoError(t, json.NewDecoder(rec.Body).Decode(&body))
	assert.Equal(t, "invalid_api_key", body["error"])
}

func TestStatus(t *testing.T) {
	srv := testServer("http://localhost:8082")
	req := httptest.NewRequest(http.MethodGet, "/admin/status", nil)
	req.Header.Set("X-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)

	var body map[string]interface{}
	require.NoError(t, json.NewDecoder(rec.Body).Decode(&body))
	assert.Equal(t, "admin", body["service"])
	assert.Equal(t, "0.1.0", body["version"])
}

func TestListPacks_ProxiesToRegistry(t *testing.T) {
	// Stand up a fake registry.
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/registry/packs", r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode([]map[string]string{
			{"id": "pack.test", "name": "Test Pack", "version": "1.0"},
		})
	}))
	defer registry.Close()

	srv := testServer(registry.URL)
	req := httptest.NewRequest(http.MethodGet, "/admin/packs", nil)
	req.Header.Set("X-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)

	var packs []map[string]string
	require.NoError(t, json.NewDecoder(rec.Body).Decode(&packs))
	assert.Len(t, packs, 1)
	assert.Equal(t, "pack.test", packs[0]["id"])
}

func TestGetPack_ProxiesToRegistry(t *testing.T) {
	registry := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/registry/packs/pack.childcare", r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"id": "pack.childcare", "name": "Childcare", "version": "1.0",
		})
	}))
	defer registry.Close()

	srv := testServer(registry.URL)
	req := httptest.NewRequest(http.MethodGet, "/admin/packs/pack.childcare", nil)
	req.Header.Set("X-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)

	var pack map[string]string
	require.NoError(t, json.NewDecoder(rec.Body).Decode(&pack))
	assert.Equal(t, "pack.childcare", pack["id"])
}

func TestListPacks_RegistryDown(t *testing.T) {
	// Use an unreachable URL.
	srv := testServer("http://127.0.0.1:1")
	req := httptest.NewRequest(http.MethodGet, "/admin/packs", nil)
	req.Header.Set("X-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)

	assert.Equal(t, http.StatusBadGateway, rec.Code)
}

func TestHealthAndReady(t *testing.T) {
	srv := testServer("http://localhost:8082")

	// /health should work without API key (infrastructure endpoint)
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)

	// /ready should work without API key
	req = httptest.NewRequest(http.MethodGet, "/ready", nil)
	rec = httptest.NewRecorder()
	srv.Router().ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
}
