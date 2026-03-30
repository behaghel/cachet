package main

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/oauth"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testServer(t *testing.T) *Server {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	return NewServerWithConfig(ServerConfig{
		Common:     common.ServerConfig{Name: "test", Version: "0.0.1", Port: "0"},
		SigningKey: key,
		Sessions:   veriff.NewInMemoryStore(),
	})
}

func TestHealthCheck(t *testing.T) {
	s := testServer(t)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), `"status":"ok"`)
}

func TestOAuthToken_Success(t *testing.T) {
	s := testServer(t)
	form := url.Values{
		"grant_type": {"client_credentials"},
		"client_id":  {"test-wallet"},
		"scope":      {"credential_issuance"},
	}
	req := httptest.NewRequest(http.MethodPost, "/oauth/token", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp oauth.TokenResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Bearer", resp.TokenType)
	assert.NotEmpty(t, resp.AccessToken)
	assert.Equal(t, 3600, resp.ExpiresIn)
}

func TestOAuthToken_InvalidGrantType(t *testing.T) {
	s := testServer(t)
	form := url.Values{"grant_type": {"invalid"}, "client_id": {"x"}}
	req := httptest.NewRequest(http.MethodPost, "/oauth/token", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestOAuthToken_MissingClientID(t *testing.T) {
	s := testServer(t)
	form := url.Values{"grant_type": {"client_credentials"}}
	req := httptest.NewRequest(http.MethodPost, "/oauth/token", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCredential_NoAuth(t *testing.T) {
	s := testServer(t)
	body, _ := json.Marshal(map[string]interface{}{"format": "jwt_vc", "types": []string{"VerifiableCredential"}})
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestCredential_InvalidFormat(t *testing.T) {
	s := testServer(t)
	token := getTestToken(t, s)

	body, _ := json.Marshal(map[string]interface{}{"format": "invalid", "types": []string{"VerifiableCredential"}})
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestVeriffWebhook_Approved(t *testing.T) {
	s := testServer(t)
	session := veriff.Session{SessionID: "s1", Status: "approved"}
	session.Verification.OverallConfidence = 0.95
	session.Verification.LivenessScore = 0.92
	session.Document.Authenticity = 0.98
	session.Verification.RiskScore = 0.02

	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Session should be stored
	stored, ok := s.sessions.Get("s1")
	assert.True(t, ok)
	assert.Equal(t, "approved", stored.Status)
}

func TestVeriffWebhook_Declined(t *testing.T) {
	s := testServer(t)
	session := veriff.Session{SessionID: "s2", Status: "declined"}
	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusAccepted, w.Code)
}

func TestVeriffWebhook_MissingSessionID(t *testing.T) {
	s := testServer(t)
	body, _ := json.Marshal(veriff.Session{Status: "approved"})
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestFullFlow_WebhookThenCredential(t *testing.T) {
	s := testServer(t)

	// 1. Webhook
	session := veriff.Session{SessionID: "flow-1", Status: "approved"}
	session.Person.DateOfBirth = "1992-03-10"
	session.Person.Confidence = 0.97
	session.Document.Country = "GB"
	session.Document.Type = "PASSPORT"
	session.Document.Authenticity = 0.99
	session.Verification.OverallConfidence = 0.98
	session.Verification.LivenessScore = 0.94
	session.Verification.RiskScore = 0.02

	webhookBody, _ := json.Marshal(session)
	wh := httptest.NewRecorder()
	s.Router().ServeHTTP(wh, httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(webhookBody)))
	require.Equal(t, http.StatusOK, wh.Code)

	// 2. Token
	token := getTestToken(t, s)

	// 3. Credential
	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "jwt_vc",
		"types":  []string{"VerifiableCredential", "IdentityCredential"},
	})
	credReq := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	credReq.Header.Set("Authorization", "Bearer "+token)
	cw := httptest.NewRecorder()
	s.Router().ServeHTTP(cw, credReq)

	assert.Equal(t, http.StatusOK, cw.Code)
	assert.Contains(t, cw.Body.String(), "VerifiableCredential")
}

func getTestToken(t *testing.T, s *Server) string {
	t.Helper()
	form := url.Values{
		"grant_type": {"client_credentials"},
		"client_id":  {"test"},
		"scope":      {"credential_issuance"},
	}
	req := httptest.NewRequest(http.MethodPost, "/oauth/token", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp oauth.TokenResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp.AccessToken
}
