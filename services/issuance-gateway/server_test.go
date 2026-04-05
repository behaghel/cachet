package main

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
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
	var resp models.TokenResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, models.Bearer, resp.TokenType)
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

func TestVeriffWebhook_NoSecret_FailsClosed(t *testing.T) {
	s := testServer(t) // no webhook secret configured
	session := veriff.Session{SessionID: "s1", Status: "approved"}
	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusInternalServerError, w.Code)
	assert.Contains(t, w.Body.String(), "server_misconfigured")
}

func TestVeriffWebhook_Approved(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	session := veriff.Session{SessionID: "s1", Status: "approved"}
	session.Verification.OverallConfidence = 0.95
	session.Verification.LivenessScore = 0.92
	session.Document.Authenticity = 0.98
	session.Verification.RiskScore = 0.02

	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", signBody(body, "test-secret"))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Session should be stored
	stored, ok := s.sessions.Get("s1")
	assert.True(t, ok)
	assert.Equal(t, "approved", stored.Status)
}

func TestVeriffWebhook_Declined(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	session := veriff.Session{SessionID: "s2", Status: "declined"}
	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", signBody(body, "test-secret"))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusAccepted, w.Code)
}

func TestVeriffWebhook_MissingSessionID(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	body, _ := json.Marshal(veriff.Session{Status: "approved"})
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", signBody(body, "test-secret"))
	w := httptest.NewRecorder()

	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func testServerWithSecret(t *testing.T, secret string) *Server {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	return NewServerWithConfig(ServerConfig{
		Common:        common.ServerConfig{Name: "test", Version: "0.0.1", Port: "0"},
		SigningKey:    key,
		Sessions:      veriff.NewInMemoryStore(),
		WebhookSecret: secret,
	})
}

func signBody(body []byte, secret string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write(body)
	return hex.EncodeToString(mac.Sum(nil))
}

func TestVeriffWebhook_HMAC_Valid(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	session := veriff.Session{SessionID: "hmac-1", Status: "approved"}
	session.Verification.OverallConfidence = 0.95
	session.Verification.LivenessScore = 0.92
	session.Document.Authenticity = 0.98
	body, _ := json.Marshal(session)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", signBody(body, "test-secret"))
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestVeriffWebhook_HMAC_Missing(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	body, _ := json.Marshal(veriff.Session{SessionID: "hmac-2", Status: "approved"})

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
	assert.Contains(t, w.Body.String(), "missing_signature")
}

func TestVeriffWebhook_HMAC_Invalid(t *testing.T) {
	s := testServerWithSecret(t, "test-secret")
	body, _ := json.Marshal(veriff.Session{SessionID: "hmac-3", Status: "approved"})

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", "deadbeef")
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
	assert.Contains(t, w.Body.String(), "invalid_signature")
}

func TestFullFlow_WebhookThenCredential(t *testing.T) {
	secret := "flow-test-secret"
	s := testServerWithSecret(t, secret)

	// 1. Webhook (with HMAC)
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
	webhookReq := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(webhookBody))
	webhookReq.Header.Set("X-HMAC-Signature", signBody(webhookBody, secret))
	wh := httptest.NewRecorder()
	s.Router().ServeHTTP(wh, webhookReq)
	require.Equal(t, http.StatusOK, wh.Code)

	// 2. Token — bound to the session
	token := getTestTokenWithSession(t, s, "flow-1")

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

func testServerWithSDJWT(t *testing.T, secret string) *Server {
	t.Helper()
	rsaKey, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	ecKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	return NewServerWithConfig(ServerConfig{
		Common:        common.ServerConfig{Name: "test", Version: "0.0.1", Port: "0"},
		SigningKey:    rsaKey,
		IssuerKey:     ecKey,
		IssuerKeyID:   "did:veriff:production#key-1",
		Sessions:      veriff.NewInMemoryStore(),
		WebhookSecret: secret,
	})
}

func TestCredential_SDJWT_Format(t *testing.T) {
	secret := "sd-jwt-test-secret"
	s := testServerWithSDJWT(t, secret)

	// 1. Webhook with HMAC
	session := veriff.Session{SessionID: "sdjwt-1", Status: "approved"}
	session.Person.DateOfBirth = "1990-05-15"
	session.Document.Country = "NL"
	session.Document.Type = "ID_CARD"
	session.Document.Authenticity = 0.97
	session.Verification.OverallConfidence = 0.96
	session.Verification.LivenessScore = 0.91
	session.Verification.RiskScore = 0.03

	webhookBody, _ := json.Marshal(session)
	webhookReq := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(webhookBody))
	webhookReq.Header.Set("X-HMAC-Signature", signBody(webhookBody, secret))
	wh := httptest.NewRecorder()
	s.Router().ServeHTTP(wh, webhookReq)
	require.Equal(t, http.StatusOK, wh.Code)

	// 2. Token
	token := getTestTokenWithSession(t, s, "sdjwt-1")

	// 3. Request vc+sd-jwt format
	credBody, _ := json.Marshal(map[string]any{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential", "IdentityCredential"},
	})
	credReq := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	credReq.Header.Set("Authorization", "Bearer "+token)
	cw := httptest.NewRecorder()
	s.Router().ServeHTTP(cw, credReq)

	assert.Equal(t, http.StatusOK, cw.Code)

	// Response should contain SD-JWT string (with ~ delimiters)
	var resp map[string]string
	require.NoError(t, json.Unmarshal(cw.Body.Bytes(), &resp))
	assert.Equal(t, "vc+sd-jwt", resp["format"])
	assert.Contains(t, resp["credential"], "~", "SD-JWT should contain ~ delimiters")

	// Should be parseable: issuerJWT~disc1~disc2~...~
	parts := strings.Split(resp["credential"], "~")
	assert.GreaterOrEqual(t, len(parts), 3, "should have issuer JWT + at least 1 disclosure + trailing empty")

	// Issuer JWT should have 3 dot-separated parts
	jwtParts := strings.Split(parts[0], ".")
	assert.Len(t, jwtParts, 3, "issuer JWT should have header.payload.signature")
}

func TestCredential_NoSession(t *testing.T) {
	s := testServer(t)
	// Token without session_id should fail at credential endpoint
	token := getTestToken(t, s)

	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "jwt_vc",
		"types":  []string{"VerifiableCredential"},
	})
	credReq := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	credReq.Header.Set("Authorization", "Bearer "+token)
	cw := httptest.NewRecorder()
	s.Router().ServeHTTP(cw, credReq)

	assert.Equal(t, http.StatusBadRequest, cw.Code)
	assert.Contains(t, cw.Body.String(), "no_session")
}

func getTestToken(t *testing.T, s *Server) string {
	t.Helper()
	return getTestTokenWithSession(t, s, "")
}

func getTestTokenWithSession(t *testing.T, s *Server, sessionID string) string {
	t.Helper()
	form := url.Values{
		"grant_type": {"client_credentials"},
		"client_id":  {"test"},
		"scope":      {"credential_issuance"},
	}
	if sessionID != "" {
		form.Set("session_id", sessionID)
	}
	req := httptest.NewRequest(http.MethodPost, "/oauth/token", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp models.TokenResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp.AccessToken
}
