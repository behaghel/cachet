package main

import (
	"bytes"
	"compress/gzip"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	jwtv5 "github.com/golang-jwt/jwt/v5"

	"github.com/cachet-id/cachet/generated/go/models"
	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/credential"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/nonce"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/statuslist"
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
		IssuerSigner:  credential.NewFileSigner(ecKey, "did:veriff:production#key-1"),
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

// ── T15: Issuance proof replay prevention ──

// TestNonce_ReturnsValidNonce verifies the /nonce endpoint issues a c_nonce.
func TestNonce_ReturnsValidNonce(t *testing.T) {
	s := testServer(t)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/nonce", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.NotEmpty(t, resp["c_nonce"])
	assert.Equal(t, float64(300), resp["c_nonce_expires_in"])
}

// TestIssuerMetadata_ReturnsDiscoveryDocument verifies /.well-known/openid-credential-issuer.
func TestIssuerMetadata_ReturnsDiscoveryDocument(t *testing.T) {
	s := testServer(t)
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/.well-known/openid-credential-issuer", nil))

	assert.Equal(t, http.StatusOK, w.Code)

	var meta map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &meta))
	assert.Contains(t, meta, "credential_issuer")
	assert.Contains(t, meta, "credential_endpoint")
	assert.Contains(t, meta, "nonce_endpoint")
	assert.Contains(t, meta, "credential_configurations_supported")
}

// TestT15_ProofJWT_HappyPath exercises the full c_nonce flow:
// webhook → token → nonce → proof JWT → credential (succeeds).
func TestT15_ProofJWT_HappyPath(t *testing.T) {
	secret := "t15-test-secret"
	s := testServerWithSDJWT(t, secret)

	// 1. Webhook
	storeVerifiedSession(t, s, secret, "t15-happy")

	// 2. Token
	token := getTestTokenWithSession(t, s, "t15-happy")

	// 3. Get c_nonce
	cNonce := getNonce(t, s)

	// 4. Build proof JWT signed by holder key
	holderKey, holderJWK := generateHolderKey(t)
	proofJWT := buildProofJWT(t, holderKey, holderJWK, cNonce, "https://cachet.id")

	// 5. Request credential with proof JWT
	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential", "IdentityCredential"},
		"proof": map[string]interface{}{
			"jwt": proofJWT,
		},
	})
	credReq := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	credReq.Header.Set("Authorization", "Bearer "+token)
	cw := httptest.NewRecorder()
	s.Router().ServeHTTP(cw, credReq)

	assert.Equal(t, http.StatusOK, cw.Code)
	var resp map[string]string
	require.NoError(t, json.Unmarshal(cw.Body.Bytes(), &resp))
	assert.Equal(t, "vc+sd-jwt", resp["format"])
	assert.Contains(t, resp["credential"], "~")
}

// TestT15_ReplayAttack_Fails verifies that reusing a proof JWT is rejected
// because the c_nonce was already consumed.
func TestT15_ReplayAttack_Fails(t *testing.T) {
	secret := "t15-replay-secret"
	s := testServerWithSDJWT(t, secret)

	storeVerifiedSession(t, s, secret, "t15-replay-1")
	storeVerifiedSession(t, s, secret, "t15-replay-2")

	// Get ONE nonce, build ONE proof JWT
	cNonce := getNonce(t, s)
	holderKey, holderJWK := generateHolderKey(t)
	proofJWT := buildProofJWT(t, holderKey, holderJWK, cNonce, "https://cachet.id")

	// First request: succeeds
	token1 := getTestTokenWithSession(t, s, "t15-replay-1")
	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential"},
		"proof":  map[string]interface{}{"jwt": proofJWT},
	})
	w1 := httptest.NewRecorder()
	req1 := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req1.Header.Set("Authorization", "Bearer "+token1)
	s.Router().ServeHTTP(w1, req1)
	require.Equal(t, http.StatusOK, w1.Code, "first use should succeed")

	// Second request with SAME proof JWT: must fail (nonce consumed)
	token2 := getTestTokenWithSession(t, s, "t15-replay-2")
	credBody2, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential"},
		"proof":  map[string]interface{}{"jwt": proofJWT},
	})
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody2))
	req2.Header.Set("Authorization", "Bearer "+token2)
	s.Router().ServeHTTP(w2, req2)

	assert.Equal(t, http.StatusBadRequest, w2.Code, "replay must be rejected")
	assert.Contains(t, w2.Body.String(), "invalid_proof")
}

// TestT15_MissingNonce_Fails verifies that a proof JWT without a nonce claim is rejected.
func TestT15_MissingNonce_Fails(t *testing.T) {
	secret := "t15-nononce-secret"
	s := testServerWithSDJWT(t, secret)
	storeVerifiedSession(t, s, secret, "t15-nononce")
	token := getTestTokenWithSession(t, s, "t15-nononce")

	holderKey, holderJWK := generateHolderKey(t)
	// Build proof JWT without nonce
	proofJWT := buildProofJWT(t, holderKey, holderJWK, "", "https://cachet.id")

	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential"},
		"proof":  map[string]interface{}{"jwt": proofJWT},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Authorization", "Bearer "+token)
	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "invalid_proof")
}

// TestT15_WrongAudience_Fails verifies that a proof JWT with wrong audience is rejected.
func TestT15_WrongAudience_Fails(t *testing.T) {
	secret := "t15-aud-secret"
	s := testServerWithSDJWT(t, secret)
	storeVerifiedSession(t, s, secret, "t15-aud")
	token := getTestTokenWithSession(t, s, "t15-aud")

	cNonce := getNonce(t, s)
	holderKey, holderJWK := generateHolderKey(t)
	// Build proof JWT with WRONG audience
	proofJWT := buildProofJWT(t, holderKey, holderJWK, cNonce, "https://evil-issuer.com")

	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential"},
		"proof":  map[string]interface{}{"jwt": proofJWT},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Authorization", "Bearer "+token)
	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	assert.Contains(t, w.Body.String(), "invalid_proof")
}

// TestT15_LegacyRawJWK_StillWorks verifies backward compat:
// raw JWK without proof JWT is accepted (with warning).
func TestT15_LegacyRawJWK_StillWorks(t *testing.T) {
	secret := "t15-legacy-secret"
	s := testServerWithSDJWT(t, secret)
	storeVerifiedSession(t, s, secret, "t15-legacy")
	token := getTestTokenWithSession(t, s, "t15-legacy")

	_, holderJWK := generateHolderKey(t)

	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential"},
		"proof": map[string]interface{}{
			"jwk": holderJWK,
		},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Authorization", "Bearer "+token)
	s.Router().ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code, "legacy path should still work")
}

// ── T15 test helpers ──

func storeVerifiedSession(t *testing.T, s *Server, secret, sessionID string) {
	t.Helper()
	session := veriff.Session{SessionID: sessionID, Status: "approved"}
	session.Person.DateOfBirth = "1990-01-15"
	session.Document.Country = "NL"
	session.Document.Type = "ID_CARD"
	session.Document.Authenticity = 0.97
	session.Verification.OverallConfidence = 0.96
	session.Verification.LivenessScore = 0.91
	session.Verification.RiskScore = 0.03

	body, _ := json.Marshal(session)
	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("X-HMAC-Signature", signBody(body, secret))
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
}

func getNonce(t *testing.T, s *Server) string {
	t.Helper()
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/nonce", nil))
	require.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp["c_nonce"].(string)
}

func generateHolderKey(t *testing.T) (*ecdsa.PrivateKey, map[string]interface{}) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	jwk := map[string]interface{}{
		"kty": "EC",
		"crv": "P-256",
		"x":   base64URLEncode(key.X.Bytes()),
		"y":   base64URLEncode(key.Y.Bytes()),
	}
	return key, jwk
}

func buildProofJWT(t *testing.T, holderKey *ecdsa.PrivateKey, holderJWK map[string]interface{}, nonce string, audience string) string {
	t.Helper()

	token := jwtv5.New(jwtv5.SigningMethodES256)
	token.Header["typ"] = "openid4vci-proof+jwt"
	token.Header["jwk"] = holderJWK

	claims := jwtv5.MapClaims{
		"aud": audience,
		"iat": time.Now().Unix(),
	}
	if nonce != "" {
		claims["nonce"] = nonce
	}
	token.Claims = claims

	signed, err := token.SignedString(holderKey)
	require.NoError(t, err)
	return signed
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

// ── ASL: Attestation Status List integration tests ──

// testServerWithASLConfig creates a server with a custom ASL config for testing.
func testServerWithASLConfig(t *testing.T, secret string, aslConfig statuslist.ASLConfig) *Server {
	t.Helper()
	rsaKey, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	ecKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	slStore := statuslist.NewStoreWithConfig(aslConfig)
	s := &Server{
		router:          common.NewRouter(common.ServerConfig{Name: "test", Version: "0.0.1", Port: "0"}),
		signingKey:      rsaKey,
		issuerSigner:    credential.NewFileSigner(ecKey, "did:veriff:production#key-1"),
		sessions:        veriff.NewInMemoryStore(),
		webhookSecret:   secret,
		statusListStore: slStore,
		nonceStore:      nonce.NewStore(),
	}

	s.router.Post("/oauth/token", s.handleOAuthToken)
	s.router.Post("/nonce", s.handleNonce)
	s.router.Post("/credential", s.handleCredentialIssuance)
	s.router.Get("/status/{listId}", s.handleGetStatusList)
	s.router.Get("/status/{listId}/info", s.handleStatusListInfo)
	s.router.Post("/status/{listId}/revoke", s.handleRevoke)
	s.router.Post("/webhooks/veriff", s.handleVeriffWebhook)
	return s
}

// issueSDJWTCredential issues an SD-JWT credential and returns the raw SD-JWT string.
func issueSDJWTCredential(t *testing.T, s *Server, sessionID string) string {
	t.Helper()
	token := getTestTokenWithSession(t, s, sessionID)
	cNonce := getNonce(t, s)
	holderKey, holderJWK := generateHolderKey(t)
	proofJWT := buildProofJWT(t, holderKey, holderJWK, cNonce, "https://cachet.id")

	credBody, _ := json.Marshal(map[string]interface{}{
		"format": "vc+sd-jwt",
		"types":  []string{"VerifiableCredential", "IdentityCredential"},
		"proof":  map[string]interface{}{"jwt": proofJWT},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Authorization", "Bearer "+token)
	s.Router().ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp["credential"]
}

// decodeIssuerJWTPayload extracts and decodes the payload from an SD-JWT's issuer JWT.
func decodeIssuerJWTPayload(t *testing.T, sdJWT string) map[string]interface{} {
	t.Helper()
	parts := strings.Split(sdJWT, "~")
	require.GreaterOrEqual(t, len(parts), 2, "SD-JWT should have issuer JWT + disclosures")

	jwtParts := strings.Split(parts[0], ".")
	require.Len(t, jwtParts, 3)

	payload, err := jwtv5.NewParser().DecodeSegment(jwtParts[1])
	require.NoError(t, err)

	var claims map[string]interface{}
	require.NoError(t, json.Unmarshal(payload, &claims))
	return claims
}

// TestASL_CredentialStatusType verifies that issued SD-JWT credentials
// contain AttestationStatusListEntry (not StatusList2021Entry).
func TestASL_CredentialStatusType(t *testing.T) {
	secret := "asl-type-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   0,
		InitialDecoyCount: 0,
	})
	storeVerifiedSession(t, s, secret, "asl-type-1")

	sdJWT := issueSDJWTCredential(t, s, "asl-type-1")
	claims := decodeIssuerJWTPayload(t, sdJWT)

	statusClaim, ok := claims["status"].(map[string]interface{})
	require.True(t, ok, "credential must have status claim")
	assert.Equal(t, "AttestationStatusListEntry", statusClaim["type"])
	assert.Equal(t, "revocation", statusClaim["statusPurpose"])
	assert.NotEmpty(t, statusClaim["statusListIndex"])
	assert.Contains(t, statusClaim["statusListCredential"].(string), "https://cachet.id/status/")
}

// TestASL_RandomIndexAllocation verifies that multiple credential issuances
// produce non-sequential status list indices.
func TestASL_RandomIndexAllocation(t *testing.T) {
	secret := "asl-random-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   0,
		InitialDecoyCount: 0,
	})

	indices := make(map[string]bool)
	for i := 0; i < 10; i++ {
		sessionID := "asl-rand-" + strings.Repeat("x", i+1) // unique session IDs
		storeVerifiedSession(t, s, secret, sessionID)
		sdJWT := issueSDJWTCredential(t, s, sessionID)
		claims := decodeIssuerJWTPayload(t, sdJWT)
		statusClaim := claims["status"].(map[string]interface{})
		idx := statusClaim["statusListIndex"].(string)
		assert.False(t, indices[idx], "duplicate index %s on issuance %d", idx, i)
		indices[idx] = true
	}

	// Indices should not be 0,1,2,...,9 (sequential)
	sequential := true
	for i := 0; i < 10; i++ {
		if !indices[fmt.Sprintf("%d", i)] {
			sequential = false
			break
		}
	}
	assert.False(t, sequential, "10 indices should not be perfectly sequential 0..9")
}

// TestASL_StatusListEndpoint_ReturnsASLType verifies that GET /status/{listId}
// returns type "AttestationStatusList" (not "BitstringStatusListCredential").
func TestASL_StatusListEndpoint_ReturnsASLType(t *testing.T) {
	secret := "asl-endpoint-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   0,
		InitialDecoyCount: 0,
	})

	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/status/1", nil))

	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "max-age=300", w.Header().Get("Cache-Control"))

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "AttestationStatusList", resp["type"])
	assert.Equal(t, "revocation", resp["purpose"])
	assert.NotEmpty(t, resp["encodedList"])
}

// TestASL_IssueThenRevoke_StatusListReflectsRevocation verifies the full
// issuance → revocation → status list check flow.
func TestASL_IssueThenRevoke_StatusListReflectsRevocation(t *testing.T) {
	secret := "asl-revoke-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   0,
		InitialDecoyCount: 0,
	})
	storeVerifiedSession(t, s, secret, "asl-revoke-1")

	// 1. Issue credential
	sdJWT := issueSDJWTCredential(t, s, "asl-revoke-1")
	claims := decodeIssuerJWTPayload(t, sdJWT)
	statusClaim := claims["status"].(map[string]interface{})
	indexStr := statusClaim["statusListIndex"].(string)

	// Parse index as int
	var index int
	_, err := json.Number(indexStr).Int64()
	require.NoError(t, err)
	require.NoError(t, json.Unmarshal([]byte(indexStr), &index))

	// 2. Status list before revocation — bit should be 0
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/status/1", nil))
	require.Equal(t, http.StatusOK, w.Code)

	var slResp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &slResp))
	bits := decodeStatusListBits(t, slResp["encodedList"])
	byteIdx := index / 8
	bitIdx := 7 - (index % 8)
	assert.Equal(t, byte(0), bits[byteIdx]>>(bitIdx)&1, "bit should be 0 before revocation")

	// 3. Revoke the credential
	revokeBody, _ := json.Marshal(map[string]int{"index": index})
	revokeReq := httptest.NewRequest(http.MethodPost, "/status/1/revoke", bytes.NewReader(revokeBody))
	rw := httptest.NewRecorder()
	s.Router().ServeHTTP(rw, revokeReq)
	require.Equal(t, http.StatusOK, rw.Code)

	// 4. Status list after revocation — bit should be 1
	w2 := httptest.NewRecorder()
	s.Router().ServeHTTP(w2, httptest.NewRequest(http.MethodGet, "/status/1", nil))
	require.Equal(t, http.StatusOK, w2.Code)

	var slResp2 map[string]string
	require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &slResp2))
	bits2 := decodeStatusListBits(t, slResp2["encodedList"])
	assert.Equal(t, byte(1), bits2[byteIdx]>>(bitIdx)&1, "bit should be 1 after revocation")
}

// TestASL_StatusListInfo_IncludesDecoys verifies the /status/{listId}/info
// endpoint reports decoy count correctly.
func TestASL_StatusListInfo_IncludesDecoys(t *testing.T) {
	secret := "asl-info-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   0,
		InitialDecoyCount: 200,
	})

	// Issue one credential to have 201 allocated (200 decoys + 1 real)
	storeVerifiedSession(t, s, secret, "asl-info-1")
	issueSDJWTCredential(t, s, "asl-info-1")

	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/status/1/info", nil))
	require.Equal(t, http.StatusOK, w.Code)

	var info map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &info))
	assert.Equal(t, float64(201), info["allocated"])
	assert.Equal(t, float64(200), info["decoys"])
	assert.Equal(t, float64(0), info["revoked"])
	assert.Equal(t, float64(131072), info["capacity"])
}

// TestASL_AnonymitySetNotMet_Returns503 verifies that the status list endpoint
// returns 503 when the anonymity set is not met.
func TestASL_AnonymitySetNotMet_Returns503(t *testing.T) {
	secret := "asl-503-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   100,
		InitialDecoyCount: 0, // no decoys — anonymity set won't be met
	})

	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/status/1", nil))

	assert.Equal(t, http.StatusServiceUnavailable, w.Code)
	assert.Equal(t, "300", w.Header().Get("Retry-After"))
	assert.Contains(t, w.Body.String(), "anonymity_set_not_met")
}

// TestASL_DecoysSatisfyAnonymitySet verifies that decoy seeding makes the
// status list immediately servable.
func TestASL_DecoysSatisfyAnonymitySet(t *testing.T) {
	secret := "asl-decoy-serve-secret"
	s := testServerWithASLConfig(t, secret, statuslist.ASLConfig{
		MinAnonymitySet:   500,
		InitialDecoyCount: 500,
	})

	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/status/1", nil))

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "AttestationStatusList", resp["type"])
	assert.NotEmpty(t, resp["encodedList"])
}

// decodeStatusListBits decodes a base64url(gzip) encoded status list into raw bytes.
func decodeStatusListBits(t *testing.T, encoded string) []byte {
	t.Helper()
	compressed, err := base64.RawURLEncoding.DecodeString(encoded)
	require.NoError(t, err)
	gz, err := gzip.NewReader(bytes.NewReader(compressed))
	require.NoError(t, err)
	defer func() { _ = gz.Close() }()
	result, err := io.ReadAll(gz)
	require.NoError(t, err)
	return result
}
