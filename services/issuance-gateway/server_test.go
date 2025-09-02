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

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
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

	// First get a token
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
	json.Unmarshal(tokenW.Body.Bytes(), &tokenResp)

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
			FirstName   string `json:"firstName"`
			LastName    string `json:"lastName"`
			DateOfBirth string `json:"dateOfBirth"`
		}{
			FirstName:   "John",
			LastName:    "Doe",
			DateOfBirth: "1990-01-01",
		},
		Document: struct {
			Number  string `json:"number"`
			Type    string `json:"type"`
			Country string `json:"country"`
		}{
			Number:  "123456789",
			Type:    "PASSPORT",
			Country: "US",
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
	}

	body, err := json.Marshal(veriffSession)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code) // Acknowledged but not processed
}
