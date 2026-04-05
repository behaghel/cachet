package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var testCfg = common.ServerConfig{Name: "verifier-test", Version: "0.0.1", Port: "0"}

// mockRegistryServer serves pack definitions for testing.
func mockRegistryServer() *httptest.Server {
	mux := http.NewServeMux()

	testPack := models.PackDefinition{
		Id:      "pack.childcare.readiness.es",
		Version: "0.1.0",
		Name:    "Childcare Readiness (Spain)",
		Purpose: "Assess suitability for paid childcare work",
		Badge:   models.BadgeDefinition{Label: "Childcare-Ready (ES)", Ttl: "P90D"},
		Predicates: []models.PredicateDefinition{
			{Id: "age.ge.18", Claim: "age", Operator: models.GreaterThanEqual, Value: float64(18), IssuersAccepted: []string{"did:veriff:*"}, ProofType: models.SdJwt},
			{Id: "identity.verified", Claim: "verified", Operator: models.Boolean, Value: true, IssuersAccepted: []string{"did:veriff:*"}, ProofType: models.SdJwt},
			{Id: "criminal.clear.es", Claim: "sexual_offences_clear", Operator: models.Boolean, Value: true, IssuersAccepted: []string{"did:justice:es"}, ProofType: models.VcBbs},
		},
	}

	mux.HandleFunc("/registry/packs", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]models.PackDefinition{testPack})
	})
	mux.HandleFunc("/registry/packs/pack.childcare.readiness.es", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(testPack)
	})
	mux.HandleFunc("/registry/packs/", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": "not_found"})
	})

	return httptest.NewServer(mux)
}

func TestNewServer(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
	assert.NotNil(t, server.packClient)
}

func TestHealthCheck(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), `"status":"ok"`)
}

func TestListPacks(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/packs", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var packs []models.Pack
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &packs))
	assert.Len(t, packs, 1)
	assert.Equal(t, "pack.childcare.readiness.es", packs[0].Id)
}

func TestVerifyPresentation_RealEval(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)

	age := 25
	verified := true
	expiry := time.Now().Add(90 * 24 * time.Hour)
	cred := models.VerifiableCredential{
		Id:             "urn:uuid:test-1",
		Issuer:         "did:veriff:production",
		IssuanceDate:   time.Now(),
		ExpirationDate: &expiry,
		CredentialSubject: models.CredentialSubject{
			Id:       "did:example:holder",
			Verified: &verified,
			PersonalData: &struct {
				Age          *int    `json:"age,omitempty"`
				DocumentType *string `json:"documentType,omitempty"`
				Nationality  *string `json:"nationality,omitempty"`
			}{Age: &age},
		},
	}

	reqBody := models.VerifyRequest{
		PolicyId: "pack.childcare.readiness.es",
		Bundle: struct {
			Credentials []models.VerifiableCredential `json:"credentials"`
		}{Credentials: []models.VerifiableCredential{cred}},
	}

	body, _ := json.Marshal(reqBody)
	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body))
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp models.VerifyResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// age.ge.18 and identity.verified should be satisfied
	assert.Contains(t, resp.Predicates, "age.ge.18")
	assert.Contains(t, resp.Predicates, "identity.verified")

	// Per-predicate results
	assert.Len(t, resp.PredicateResults, 3)
	resultMap := map[string]models.PredicateResultStatus{}
	for _, r := range resp.PredicateResults {
		resultMap[r.PredicateId] = r.Status
	}
	assert.Equal(t, models.Satisfied, resultMap["age.ge.18"])
	assert.Equal(t, models.Satisfied, resultMap["identity.verified"])
	assert.Equal(t, models.NotEvaluable, resultMap["criminal.clear.es"])

	// Cachet NOT granted (criminal.clear.es is required but not evaluable)
	assert.Equal(t, "", resp.Cachet)
	assert.False(t, resp.Summary.CachetGranted)
	assert.Equal(t, 2, resp.Summary.RequiredSatisfied)
	assert.Equal(t, 3, resp.Summary.RequiredTotal)
}

func TestVerifyPresentation_UnknownPack(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	reqBody := models.VerifyRequest{
		PolicyId: "nonexistent.pack",
		Bundle: struct {
			Credentials []models.VerifiableCredential `json:"credentials"`
		}{Credentials: []models.VerifiableCredential{}},
	}
	body, _ := json.Marshal(reqBody)
	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body))
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestVerifyPresentation_InvalidJSON(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	req := httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader([]byte("bad")))
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRouteNotFound(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	server := NewServer(testCfg, registry.URL)
	w := httptest.NewRecorder()
	server.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/nonexistent", nil))
	assert.Equal(t, http.StatusNotFound, w.Code)
}
