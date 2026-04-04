package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var testCfg = common.ServerConfig{Name: "registry-test", Version: "0.0.1", Port: "0"}

func TestNewServer(t *testing.T) {
	server := NewServer(testCfg)
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
	assert.NotNil(t, server.packStore)
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

func TestListPacks(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/registry/packs", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var packs []models.PackDefinition
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &packs))
	assert.GreaterOrEqual(t, len(packs), 5) // 4 childcare + 1 safe-seller
}

func TestGetPack_Found(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/registry/packs/pack.childcare.readiness.es", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var pack models.PackDefinition
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &pack))
	assert.Equal(t, "pack.childcare.readiness.es", pack.Id)
	assert.Equal(t, "Childcare Readiness (Spain)", pack.Name)
	assert.Len(t, pack.Predicates, 5)

	// Verify the age predicate
	agePred := pack.Predicates[0]
	assert.Equal(t, "age.ge.18", agePred.Id)
	assert.Equal(t, "age", agePred.Claim)
	assert.Equal(t, models.GreaterThanEqual, agePred.Operator)
	assert.Equal(t, models.SdJwt, agePred.ProofType)
}

func TestGetPack_NotFound(t *testing.T) {
	server := NewServer(testCfg)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/registry/packs/nonexistent", nil))

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRouteNotFound(t *testing.T) {
	server := NewServer(testCfg)

	req := httptest.NewRequest(http.MethodGet, "/nonexistent", nil)
	w := httptest.NewRecorder()

	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}
