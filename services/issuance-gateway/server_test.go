package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/behaghel/cachet/services/common/config"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// setupTestEnv configures environment for testing
const testWebhookSecret = "test-veriff-webhook-secret"

func setupTestEnv(t *testing.T) func() {
	// Set webhook secret to deterministic value for signature validation
	originalSecret := os.Getenv("VERIFF_WEBHOOK_SECRET")
	if err := os.Setenv("VERIFF_WEBHOOK_SECRET", testWebhookSecret); err != nil {
		t.Fatalf("failed to set VERIFF_WEBHOOK_SECRET: %v", err)
	}

	// Return cleanup function
	return func() {
		if originalSecret == "" {
			_ = os.Unsetenv("VERIFF_WEBHOOK_SECRET")
		} else {
			_ = os.Setenv("VERIFF_WEBHOOK_SECRET", originalSecret)
		}
	}
}

func signWebhookPayload(payload []byte) string {
	mac := hmac.New(sha256.New, []byte(testWebhookSecret))
	mac.Write(payload)
	return "sha256=" + hex.EncodeToString(mac.Sum(nil))
}

// Types are now defined in server.go

// Helper function to create test VeriffSession with enhanced structure
func createTestVeriffSession(sessionID, status string) VeriffSession {
	session := VeriffSession{
		SessionID: sessionID,
		Status:    status,
		Id:        sessionID,
	}

	// Set basic person data
	session.Person.FirstName = "Alice"
	session.Person.LastName = "Johnson"
	session.Person.FullName = "Alice Johnson"
	session.Person.DateOfBirth = "1992-03-10"
	session.Person.Nationality = "GB"
	session.Person.Gender = "F"
	session.Person.Confidence = 0.97
	session.Person.FirstNameConfidence = 0.95
	session.Person.DateOfBirthConfidence = 0.98

	// Set basic document data
	session.Document.Number = "AB123456C"
	session.Document.Type = "PASSPORT"
	session.Document.Country = "GB"
	session.Document.FirstName = "Alice"
	session.Document.LastName = "Johnson"
	session.Document.DateOfBirth = "1992-03-10"
	session.Document.IssueDate = "2020-01-01"
	session.Document.ExpiryDate = "2030-01-01"
	session.Document.Authenticity = 0.99
	session.Document.ImageQuality = 0.95
	session.Document.OcrConfidence = 0.92
	session.Document.IssuerRecognized = true
	session.Document.IssuerTrustScore = 0.98
	session.Document.CrossBorderValid = true

	// Set security features
	session.Document.SecurityFeatures.Holograms = true
	session.Document.SecurityFeatures.Watermarks = true
	session.Document.SecurityFeatures.MicroText = true
	session.Document.SecurityFeatures.RfidRead = false
	session.Document.SecurityFeatures.OverallScore = 0.85

	// Set face data
	session.Face.Quality = 0.90
	session.Face.Confidence = 0.95
	session.Face.UniquenessScore = 0.88
	session.Face.TemplateQuality = 0.92
	session.Face.SpoofingDetection.Screen = false
	session.Face.SpoofingDetection.Mask = false
	session.Face.SpoofingDetection.Photo = false
	session.Face.SpoofingDetection.Video = false
	session.Face.SpoofingDetection.DeepfakeScore = 0.02
	session.Face.SpoofingDetection.OverallScore = 0.98

	// Set verification data
	session.Verification.LivenessScore = 0.94
	session.Verification.OverallConfidence = 0.98
	session.Verification.RiskScore = 0.02
	session.Verification.Timestamp = "2025-09-07T11:30:00Z"

	// Set risk data
	session.Risk.BehavioralScore = 0.05
	session.Risk.SanctionsChecked = true
	session.Risk.SanctionsMatch = false
	session.Risk.SanctionsConfidence = 0.99
	session.Risk.PepsChecked = true
	session.Risk.PepsMatch = false
	session.Risk.PepsRiskLevel = "Low"
	session.Risk.PepsConfidence = 0.99

	// Set device data
	session.Device.TrustScore = 0.92
	session.Device.JailbrokenRooted = false
	session.Device.EmulatorDetected = false
	session.Device.VpnDetected = false
	session.Device.ProxyDetected = false
	session.Device.Fingerprint = "test-device-123"

	// Set geolocation data
	session.Geolocation.ConsistentWithId = true
	session.Geolocation.HighRiskCountry = false
	session.Geolocation.Spoofed = false
	session.Geolocation.TravelPatternNormal = true
	session.Geolocation.Confidence = 0.95

	// Set session context
	session.SessionDuration = 180
	session.AttemptCount = 1
	session.UserCooperationScore = 0.98
	session.TechnicalQualityScore = 0.95
	session.CompletionRate = 1.0
	session.Method = "mobile_app"
	session.RequiredOperatorReview = false
	session.AiConfidenceScore = 0.96

	return session
}

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
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()

	// First set up a Veriff session via webhook
	veriffSession := createTestVeriffSession("test-session-456", "approved")

	// Send Veriff webhook
	veriffBody, err := json.Marshal(veriffSession)
	require.NoError(t, err)
	veriffReq := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(veriffBody))
	veriffReq.Header.Set("Content-Type", "application/json")
	veriffReq.Header.Set("X-HMAC-SIGNATURE", signWebhookPayload(veriffBody))
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
	err = json.Unmarshal(tokenW.Body.Bytes(), &tokenResp)
	require.NoError(t, err)

	// Now request credential
	credReq := CredentialRequest{
		Format:    "jwt_vc",
		Types:     []string{"VerifiableCredential", "IdentityCredential"},
		SessionID: "test-session-456",
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
		Format:    "jwt_vc",
		Types:     []string{"VerifiableCredential", "IdentityCredential"},
		SessionID: "test-session",
	}

	credBody, _ := json.Marshal(credReq)
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(credBody))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestCredentialEndpoint_MissingSessionID(t *testing.T) {
	server := NewServer()

	tokenReq := TokenRequest{
		GrantType: "client_credentials",
		ClientID:  "test-wallet",
		Scope:     "credential_issuance",
	}

	tokenBody, _ := json.Marshal(tokenReq)
	tokenHttpReq := httptest.NewRequest(http.MethodPost, "/oauth/token", bytes.NewReader(tokenBody))
	tokenHttpReq.Header.Set("Content-Type", "application/json")
	tokenRecorder := httptest.NewRecorder()
	server.router.ServeHTTP(tokenRecorder, tokenHttpReq)

	var tokenResp TokenResponse
	require.NoError(t, json.Unmarshal(tokenRecorder.Body.Bytes(), &tokenResp))

	credReq := CredentialRequest{
		Format:    "jwt_vc",
		Types:     []string{"VerifiableCredential"},
		SessionID: "",
	}

	body, _ := json.Marshal(credReq)
	req := httptest.NewRequest(http.MethodPost, "/credential", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+tokenResp.AccessToken)
	resp := httptest.NewRecorder()

	server.router.ServeHTTP(resp, req)

	assert.Equal(t, http.StatusBadRequest, resp.Code)
}

func TestVeriffWebhook_Success(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()

	veriffSession := createTestVeriffSession("test-session-123", "approved")
	// Customize for this test
	veriffSession.Person.FirstName = "John"
	veriffSession.Person.LastName = "Doe"
	veriffSession.Person.FullName = "John Doe"
	veriffSession.Person.DateOfBirth = "1990-01-01"
	veriffSession.Document.Number = "123456789"
	veriffSession.Document.Country = "US"

	body, err := json.Marshal(veriffSession)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-HMAC-SIGNATURE", signWebhookPayload(body))
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestVeriffWebhook_AliasHookPath(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()
	body, err := json.Marshal(createTestVeriffSession("test-session-456", "approved"))
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/hook", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-HMAC-SIGNATURE", signWebhookPayload(body))

	w := httptest.NewRecorder()
	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestVeriffWebhook_InvalidStatus(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()

	veriffSession := createTestVeriffSession("test-session-123", "declined")
	// Customize for declined test - lower quality scores
	veriffSession.Person.FirstName = "Jane"
	veriffSession.Person.LastName = "Smith"
	veriffSession.Person.FullName = "Jane Smith"
	veriffSession.Person.DateOfBirth = "1985-05-15"
	veriffSession.Person.Confidence = 0.45
	veriffSession.Document.Number = "987654321"
	veriffSession.Document.Type = "DRIVERS_LICENSE"
	veriffSession.Document.Country = "CA"
	veriffSession.Document.Authenticity = 0.35
	veriffSession.Verification.LivenessScore = 0.30
	veriffSession.Verification.OverallConfidence = 0.25
	veriffSession.Verification.RiskScore = 0.85
	veriffSession.Verification.Timestamp = "2025-09-06T20:45:00Z"

	body, err := json.Marshal(veriffSession)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-HMAC-SIGNATURE", signWebhookPayload(body))
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code) // Acknowledged but not processed
}

func TestVeriffWebhook_MissingSignature(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()
	body := []byte(`{"session_id":"test-session","status":"approved"}`)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestVeriffWebhook_InvalidSignature(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()
	body := []byte(`{"session_id":"test-session","status":"approved"}`)

	req := httptest.NewRequest(http.MethodPost, "/webhooks/veriff", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-HMAC-SIGNATURE", "sha256=deadbeef")
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestVerifyWebhookSignature(t *testing.T) {
	payload := []byte(`{"status":"approved"}`)
	signature := signWebhookPayload(payload)

	assert.True(t, verifyWebhookSignature(payload, signature, testWebhookSecret))
	assert.True(t, verifyWebhookSignature(payload, strings.ToUpper(signature), testWebhookSecret))
	assert.False(t, verifyWebhookSignature(payload, signature, "wrong"))
	assert.False(t, verifyWebhookSignature(payload, "", testWebhookSecret))
}

func TestResolveVeriffCallbackURL_ConvertsNgrokToHTTPS(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "https://issuer.example.com"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "\"http://c3d47af68db6.ngrok-free.app/sessions/veriff\"")

	callbackURL, err := s.resolveVeriffCallbackURL()
	require.NoError(t, err)
	assert.Equal(t, "https://c3d47af68db6.ngrok-free.app/sessions/veriff", callbackURL)
}

func TestResolveVeriffCallbackURL_DefaultsToConfig(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "https://issuer.example.com"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "")

	callbackURL, err := s.resolveVeriffCallbackURL()
	require.NoError(t, err)
	assert.Equal(t, "https://issuer.example.com/webhooks/veriff", callbackURL)
}

func TestResolveVeriffCallbackURL_ErrorsForInsecureConfig(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "http://localhost:8090"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "")

	_, err := s.resolveVeriffCallbackURL()
	require.Error(t, err)
	assert.Contains(t, err.Error(), "https")
}

func TestResolveVeriffCallbackURL_ErrorsForInsecureExternalURL(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "https://issuer.example.com"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "http://localhost:8080/callback")

	_, err := s.resolveVeriffCallbackURL()
	require.Error(t, err)
	assert.Contains(t, err.Error(), "https")
}

func TestResolveVeriffCallbackURLForRequest_UsesRequestHostWhenConfiguredHostDiffers(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "https://staging-issuance.cachet.dev"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "")
	req := httptest.NewRequest(http.MethodPost, "/sessions/veriff", http.NoBody)
	req.Host = "cachet-issuance-gateway-tncrtr5uha-uc.a.run.app"
	req.Header.Set("X-Forwarded-Proto", "https")

	callbackURL, err := s.resolveVeriffCallbackURLForRequest(req)
	require.NoError(t, err)
	assert.Equal(t, "https://cachet-issuance-gateway-tncrtr5uha-uc.a.run.app/webhooks/veriff", callbackURL)
}

func TestResolveVeriffCallbackURLForRequest_FallbacksWhenConfigInvalid(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "http://localhost:8090"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "")
	req := httptest.NewRequest(http.MethodPost, "/sessions/veriff", http.NoBody)
	req.Host = "cachet-issuance-gateway-tncrtr5uha-uc.a.run.app"
	req.Header.Set("X-Forwarded-Proto", "https")

	callbackURL, err := s.resolveVeriffCallbackURLForRequest(req)
	require.NoError(t, err)
	assert.Equal(t, "https://cachet-issuance-gateway-tncrtr5uha-uc.a.run.app/webhooks/veriff", callbackURL)
}

func TestGetVeriffAPIKey_IgnoresInvalidUUID(t *testing.T) {
	s := &Server{}
	t.Setenv("VERIFF_API_KEY", "placeholder-veriff-api-key")
	t.Setenv("VERIFF_TEST_API_KEY", "")
	t.Setenv("VERIFF_PROD_API_KEY", "")

	require.Equal(t, "", s.getVeriffAPIKey())
}

func TestGetVeriffAPIKey_ReturnsValidUUID(t *testing.T) {
	s := &Server{}
	const validKey = "123e4567-e89b-12d3-a456-426614174000"
	t.Setenv("VERIFF_API_KEY", validKey)
	t.Setenv("VERIFF_TEST_API_KEY", "")
	t.Setenv("VERIFF_PROD_API_KEY", "")

	assert.Equal(t, validKey, s.getVeriffAPIKey())
}

func TestResolveVeriffCallbackURL_AppendsDefaultPathWhenMissing(t *testing.T) {
	s := &Server{
		config: &config.Config{
			Services: config.ServicesConfig{
				IssuanceGateway: config.ServiceConfig{PublicURL: "https://issuer.example.com"},
			},
		},
	}

	t.Setenv("VERIFF_WEBHOOK_EXTERNAL_URL", "https://c3d47af68db6.ngrok-free.app")

	callbackURL, err := s.resolveVeriffCallbackURL()
	require.NoError(t, err)
	assert.Equal(t, "https://c3d47af68db6.ngrok-free.app/webhooks/veriff", callbackURL)
}

type roundTripFunc func(*http.Request) *http.Response

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req), nil
}

func TestGetVeriffSessionStatus_ReturnsCachedStatus(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	server := NewServer()
	server.sessionMu.Lock()
	server.verifiedSessions["session-123"] = VeriffSession{SessionID: "session-123", Status: "approved"}
	server.sessionMu.Unlock()

	req := httptest.NewRequest(http.MethodGet, "/sessions/veriff/session-123", nil)
	w := httptest.NewRecorder()

	server.router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var body map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Equal(t, "approved", body["status"])
}

func TestGetVeriffSessionStatus_FetchesFromAPI(t *testing.T) {
	cleanup := setupTestEnv(t)
	defer cleanup()

	t.Setenv("VERIFF_API_KEY", "test-api-key")
	t.Setenv("VERIFF_BASE_URL", "https://stationapi.veriff.com")

	server := NewServer()
	server.httpClient = &http.Client{
		Transport: roundTripFunc(func(req *http.Request) *http.Response {
			assert.Contains(t, req.URL.Path, "/v1/sessions/session-456")
			return &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"session":{"id":"session-456","status":"approved","vendorData":"cachet-android-wallet"}}`)),
				Header:     make(http.Header),
			}
		}),
	}

	req := httptest.NewRequest(http.MethodGet, "/sessions/veriff/session-456", nil)
	w := httptest.NewRecorder()
	server.router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)
	var body map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Equal(t, "approved", body["status"])
}
