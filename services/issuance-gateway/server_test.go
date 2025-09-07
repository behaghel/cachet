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

// Types are now defined in server.go

func TestNewServer(t *testing.T) {
	server := NewServer()
	assert.NotNil(t, server)
	assert.NotNil(t, server.router)
}

func TestHealthCheck(t *testing.T) {
	server := NewServer()

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "ok", w.Body.String())
}

func TestOAuth2TokenEndpoint_Success(t *testing.T) {
	server := NewServer()

	tokenReq := TokenRequest{
		GrantType: "client_credentials",
		ClientID:  "test-wallet",
		Scope:     "credential_issuance",
	}

	body, err := json.Marshal(tokenReq)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/oauth/token", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var tokenResp TokenResponse
	err = json.Unmarshal(w.Body.Bytes(), &tokenResp)
	require.NoError(t, err)

	assert.Equal(t, "Bearer", tokenResp.TokenType)
	assert.NotEmpty(t, tokenResp.AccessToken)
	assert.Equal(t, 3600, tokenResp.ExpiresIn)
	assert.Equal(t, "credential_issuance", tokenResp.Scope)
}

func TestOAuth2TokenEndpoint_InvalidGrantType(t *testing.T) {
	server := NewServer()

	tokenReq := TokenRequest{
		GrantType: "invalid_grant",
		ClientID:  "test-wallet",
		Scope:     "credential_issuance",
	}

	body, err := json.Marshal(tokenReq)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/oauth/token", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCredentialEndpoint_Success(t *testing.T) {
	server := NewServer()

	// First set up a Veriff session via webhook
	veriffSession := VeriffSession{
		SessionID: "test-session-456",
		Status:    "approved",
		Person: struct {
			FirstName   string  `json:"firstName"`
			LastName    string  `json:"lastName"`
			DateOfBirth string  `json:"dateOfBirth"`
			Confidence  float64 `json:"confidence,omitempty"`
		}{
			FirstName:   "Alice",
			LastName:    "Johnson",
			DateOfBirth: "1992-03-10",
			Confidence:  0.97,
		},
		Document: struct {
			Number       string  `json:"number"`
			Type         string  `json:"type"`
			Country      string  `json:"country"`
			Authenticity float64 `json:"authenticity,omitempty"`
		}{
			Number:       "AB123456C",
			Type:         "PASSPORT",
			Country:      "GB",
			Authenticity: 0.99,
		},
		Verification: struct {
			LivenessScore     float64 `json:"liveness_score,omitempty"`
			OverallConfidence float64 `json:"overall_confidence,omitempty"`
			RiskScore         float64 `json:"risk_score,omitempty"`
			Timestamp         string  `json:"timestamp,omitempty"`
		}{
			LivenessScore:     0.94,
			OverallConfidence: 0.98,
			RiskScore:         0.02,
			Timestamp:         "2025-09-07T11:30:00Z",
		},
	}

	// Send Veriff webhook
	veriffBody, err := json.Marshal(veriffSession)
	require.NoError(t, err)
	veriffReq := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(veriffBody))
	veriffReq.Header.Set("Content-Type", "application/json")
	veriffW := httptest.NewRecorder()
	server.router.ServeHTTP(veriffW, veriffReq)
	require.Equal(t, http.StatusOK, veriffW.Code)

	// Now get a token
	tokenReq := TokenRequest{
		GrantType: "client_credentials",
		ClientID:  "test-wallet",
		Scope:     "credential_issuance",
	}

	tokenBody, _ := json.Marshal(tokenReq)
	tokenHttpReq := httptest.NewRequest(http.MethodPost, "/oauth/token", bytes.NewReader(tokenBody))
	tokenHttpReq.Header.Set("Content-Type", "application/json")
	tokenW := httptest.NewRecorder()
	server.router.ServeHTTP(tokenW, tokenHttpReq)

	var tokenResp TokenResponse
	err := json.Unmarshal(tokenW.Body.Bytes(), &tokenResp)
	require.NoError(t, err)

	// Now request credential
	credReq := CredentialRequest{
		Format: "jwt_vc",
		Types:  []string{"VerifiableCredential", "IdentityCredential"},
	}

	credBody, err := json.Marshal(credReq)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+tokenResp.AccessToken)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var credResp CredentialResponse
	err = json.Unmarshal(w.Body.Bytes(), &credResp)
	require.NoError(t, err)

	assert.Equal(t, "jwt_vc", credResp.Format)
	assert.NotNil(t, credResp.Credential)
}

func TestCredentialEndpoint_NoAuth(t *testing.T) {
	server := NewServer()

	credReq := CredentialRequest{
		Format: "jwt_vc",
		Types:  []string{"VerifiableCredential", "IdentityCredential"},
	}

	credBody, _ := json.Marshal(credReq)
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestVeriffWebhook_Success(t *testing.T) {
	server := NewServer()

	veriffSession := VeriffSession{
		SessionID: "test-session-123",
		Status:    "approved",
		Person: struct {
			FirstName   string  `json:"firstName"`
			LastName    string  `json:"lastName"`
			DateOfBirth string  `json:"dateOfBirth"`
			Confidence  float64 `json:"confidence,omitempty"`
		}{
			FirstName:   "John",
			LastName:    "Doe",
			DateOfBirth: "1990-01-01",
			Confidence:  0.95,
		},
		Document: struct {
			Number       string  `json:"number"`
			Type         string  `json:"type"`
			Country      string  `json:"country"`
			Authenticity float64 `json:"authenticity,omitempty"`
		}{
			Number:       "123456789",
			Type:         "PASSPORT",
			Country:      "US",
			Authenticity: 0.98,
		},
		Verification: struct {
			LivenessScore     float64 `json:"liveness_score,omitempty"`
			OverallConfidence float64 `json:"overall_confidence,omitempty"`
			RiskScore         float64 `json:"risk_score,omitempty"`
			Timestamp         string  `json:"timestamp,omitempty"`
		}{
			LivenessScore:     0.92,
			OverallConfidence: 0.96,
			RiskScore:         0.05,
			Timestamp:         "2025-09-06T21:00:00Z",
		},
	}

	body, err := json.Marshal(veriffSession)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestVeriffWebhook_InvalidStatus(t *testing.T) {
	server := NewServer()

	veriffSession := VeriffSession{
		SessionID: "test-session-123",
		Status:    "declined",
		Person: struct {
			FirstName   string  `json:"firstName"`
			LastName    string  `json:"lastName"`
			DateOfBirth string  `json:"dateOfBirth"`
			Confidence  float64 `json:"confidence,omitempty"`
		}{
			FirstName:   "Jane",
			LastName:    "Smith",
			DateOfBirth: "1985-05-15",
			Confidence:  0.45, // Lower confidence for declined status
		},
		Document: struct {
			Number       string  `json:"number"`
			Type         string  `json:"type"`
			Country      string  `json:"country"`
			Authenticity float64 `json:"authenticity,omitempty"`
		}{
			Number:       "987654321",
			Type:         "DRIVERS_LICENSE",
			Country:      "CA",
			Authenticity: 0.35, // Lower authenticity for declined status
		},
		Verification: struct {
			LivenessScore     float64 `json:"liveness_score,omitempty"`
			OverallConfidence float64 `json:"overall_confidence,omitempty"`
			RiskScore         float64 `json:"risk_score,omitempty"`
			Timestamp         string  `json:"timestamp,omitempty"`
		}{
			LivenessScore:     0.30, // Low liveness score
			OverallConfidence: 0.25, // Low overall confidence
			RiskScore:         0.85, // High risk score for declined status
			Timestamp:         "2025-09-06T20:45:00Z",
		},
	}

	body, err := json.Marshal(veriffSession)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code) // Acknowledged but not processed
}
