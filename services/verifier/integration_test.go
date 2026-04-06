package main

import (
	"bytes"
	"crypto/ecdh"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"math/big"

	"github.com/go-chi/chi/v5"
	gojwt "github.com/golang-jwt/jwt/v5"
	"github.com/lestrrat-go/jwx/v2/jwa"
	"github.com/lestrrat-go/jwx/v2/jwk"
	jwxjwt "github.com/lestrrat-go/jwx/v2/jwt"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/verifier/internal/eval"
	"github.com/cachet-id/cachet/services/verifier/internal/identity"
	"github.com/cachet-id/cachet/services/verifier/internal/jwe"
)

// TestRelayJWEVerificationFlow exercises the full E2E flow:
//
//	Verifier creates session (nonce + ephemeral X25519 key)
//	→ Holder builds SD-JWT with KB-JWT
//	→ Holder encrypts presentation (JWE) to verifier's ephemeral key
//	→ Holder posts JWE to relay
//	→ Verifier polls relay for response
//	→ Verifier sends JWE to backend for decryption + verification
//	→ Backend decrypts JWE, verifies SD-JWT + KB-JWT, evaluates predicates
//
// This test catches:
// - JWE encrypt/decrypt round-trip compatibility
// - Session ephemeral key wiring
// - Null/empty JSON field handling in responses
// - Pack ID resolution
// - KB-JWT nonce + audience binding through the relay
func TestRelayJWEVerificationFlow(t *testing.T) {
	// ── Setup: issuer key + holder key ──
	issuerKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	holderKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	// ── Setup: verifier server with DID resolver ──
	registry := mockRegistryServer()
	defer registry.Close()

	verifier := NewServerWithConfig(VerifierConfig{
		Common:      common.ServerConfig{Name: "integration-test", Version: "0.0.1", Port: "0"},
		RegistryURL: registry.URL,
		VerifierDID: "did:web:verifier.test",
		DIDResolver: eval.NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
			"did:veriff:production": &issuerKey.PublicKey,
		}),
	})

	// ── Step 1: Verifier creates session ──
	w := httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", nil))
	require.Equal(t, http.StatusOK, w.Code)

	var session struct {
		SessionID       string `json:"sessionId"`
		Nonce           string `json:"nonce"`
		VerifierDID     string `json:"verifierDid"`
		EphemeralPubKey string `json:"ephemeralPubKey"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &session))
	assert.NotEmpty(t, session.SessionID, "session ID must be set")
	assert.NotEmpty(t, session.Nonce, "nonce must be set")
	assert.NotEmpty(t, session.EphemeralPubKey, "ephemeral public key must be set")

	// ── Step 2: Build SD-JWT credential with holder binding ──
	cnfClaim := map[string]interface{}{
		"jwk": map[string]interface{}{
			"kty": "EC",
			"crv": "P-256",
			"x":   base64.RawURLEncoding.EncodeToString(holderKey.X.Bytes()),
			"y":   base64.RawURLEncoding.EncodeToString(holderKey.Y.Bytes()),
		},
	}
	sdJWT := buildIntegrationSDJWT(t, issuerKey, "did:veriff:production",
		map[string]interface{}{
			"age":      float64(25),
			"verified": true,
		}, cnfClaim)

	// ── Step 3: Build KB-JWT with session nonce + audience ──
	sdHash := computeSDHash(sdJWT)
	kbjwt := buildIntegrationKBJWT(t, holderKey, session.Nonce, session.VerifierDID, sdHash)
	presentation := sdJWT + kbjwt

	// ── Step 4: Encrypt presentation (JWE) to verifier's ephemeral key ──
	pubKeyBytes, err := base64.RawURLEncoding.DecodeString(session.EphemeralPubKey)
	require.NoError(t, err)
	pubKey, err := ecdh.X25519().NewPublicKey(pubKeyBytes)
	require.NoError(t, err)

	encrypted, err := jwe.Encrypt([]byte(presentation), pubKey)
	require.NoError(t, err)
	assert.True(t, jwe.IsJWE(encrypted), "encrypted output must be JWE format")
	assert.NotContains(t, encrypted, "~", "JWE must not contain SD-JWT separator")

	// ── Step 5: Send JWE to verifier for decryption + verification ──
	verifyReq := map[string]interface{}{
		"policyId":         "pack.childcare.readiness.es",
		"sessionId":        session.SessionID,
		"sdJwtCredentials": []string{encrypted},
	}
	body, err := json.Marshal(verifyReq)
	require.NoError(t, err)

	w = httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body)))
	require.Equal(t, http.StatusOK, w.Code, "verify must succeed; body: %s", w.Body.String())

	var resp struct {
		Cachet           string          `json:"cachet"`
		Predicates       json.RawMessage `json:"predicates"` // nullable — test this!
		Freshness        string          `json:"freshness"`
		PredicateResults []struct {
			PredicateId string `json:"predicateId"`
			Status      string `json:"status"`
		} `json:"predicateResults"`
		Summary struct {
			CachetGranted     bool `json:"cachetGranted"`
			RequiredSatisfied int  `json:"requiredSatisfied"`
			RequiredTotal     int  `json:"requiredTotal"`
		} `json:"summary"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// ── Assertions ──
	resultMap := map[string]string{}
	for _, r := range resp.PredicateResults {
		resultMap[r.PredicateId] = r.Status
	}

	assert.Equal(t, "satisfied", resultMap["age.ge.18"], "age >= 18 should pass for age 25")
	assert.Equal(t, "satisfied", resultMap["identity.verified"], "verified=true should pass")
	assert.Equal(t, "no_credential", resultMap["criminal.clear.es"], "BBS+ predicate has no matching credential")

	assert.Equal(t, 2, resp.Summary.RequiredSatisfied)
	assert.Equal(t, 3, resp.Summary.RequiredTotal)
	// Cachet not granted because criminal.clear.es is required but not evaluable
	assert.False(t, resp.Summary.CachetGranted)
}

// TestPlaintextFallback verifies backward compat: plaintext SD-JWT still works
// when a session has an ephemeral key but the credential is not JWE-encrypted.
func TestPlaintextFallback(t *testing.T) {
	issuerKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	registry := mockRegistryServer()
	defer registry.Close()

	verifier := NewServerWithConfig(VerifierConfig{
		Common:      common.ServerConfig{Name: "integration-test", Version: "0.0.1", Port: "0"},
		RegistryURL: registry.URL,
		VerifierDID: "did:web:verifier.test",
		DIDResolver: eval.NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
			"did:veriff:production": &issuerKey.PublicKey,
		}),
	})

	// Create session (has ephemeral key)
	w := httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", nil))
	require.Equal(t, http.StatusOK, w.Code)

	var session struct {
		SessionID   string `json:"sessionId"`
		Nonce       string `json:"nonce"`
		VerifierDID string `json:"verifierDid"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &session))

	// Build plaintext SD-JWT (no encryption)
	sdJWT := buildIntegrationSDJWT(t, issuerKey, "did:veriff:production",
		map[string]interface{}{"age": float64(30)}, nil)

	verifyReq := map[string]interface{}{
		"policyId":         "pack.childcare.readiness.es",
		"sessionId":        session.SessionID,
		"sdJwtCredentials": []string{sdJWT},
	}
	body, _ := json.Marshal(verifyReq)

	w = httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/presentations/verify", bytes.NewReader(body)))
	require.Equal(t, http.StatusOK, w.Code, "plaintext SD-JWT must still work; body: %s", w.Body.String())
}

// TestRelayRoundTrip exercises the relay's session lifecycle in-process.
func TestRelayRoundTrip(t *testing.T) {
	relaySrv := newTestRelayServer()

	// Create relay session
	requestPayload := []byte(`{"nonce":"test","verifierDid":"did:web:v","packId":"test"}`)
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader(requestPayload))
	relaySrv.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var relaySession struct {
		SessionID   string `json:"sessionId"`
		RequestURI  string `json:"requestUri"`
		ResponseURI string `json:"responseUri"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &relaySession))

	// Holder fetches request
	w = httptest.NewRecorder()
	relaySrv.ServeHTTP(w, httptest.NewRequest(http.MethodGet, relaySession.RequestURI, nil))
	require.Equal(t, http.StatusOK, w.Code)
	assert.JSONEq(t, string(requestPayload), w.Body.String())

	// Verifier polls — no response yet
	w = httptest.NewRecorder()
	relaySrv.ServeHTTP(w, httptest.NewRequest(http.MethodGet, relaySession.ResponseURI, nil))
	assert.Equal(t, http.StatusNoContent, w.Code)

	// Holder posts response
	responsePayload := []byte("encrypted-jwe-payload")
	w = httptest.NewRecorder()
	relaySrv.ServeHTTP(w, httptest.NewRequest(http.MethodPost, relaySession.ResponseURI, bytes.NewReader(responsePayload)))
	require.Equal(t, http.StatusNoContent, w.Code)

	// Verifier polls — response available
	w = httptest.NewRecorder()
	relaySrv.ServeHTTP(w, httptest.NewRequest(http.MethodGet, relaySession.ResponseURI, nil))
	require.Equal(t, http.StatusOK, w.Code)
	respBody, _ := io.ReadAll(w.Body)
	assert.Equal(t, responsePayload, respBody)
}

// TestSignedRequestObject_DIDDocument verifies the full Slice 7 chain:
// 1. Session response includes a signed requestObject JWT
// 2. The DID document endpoint returns the verifier's public key
// 3. The requestObject signature is valid against that key
// 4. The requestObject contains the correct claims
func TestSignedRequestObject_DIDDocument(t *testing.T) {
	registry := mockRegistryServer()
	defer registry.Close()

	verifierDID := "did:web:verifier.test"
	signer := identity.NewDevSigner(verifierDID)

	verifier := NewServerWithConfig(VerifierConfig{
		Common:         common.ServerConfig{Name: "integration-test", Version: "0.0.1", Port: "0"},
		RegistryURL:    registry.URL,
		VerifierDID:    verifierDID,
		IdentitySigner: signer,
	})

	// Step 1: Create session with pack metadata
	reqBody := `{"packId":"pack.childcare.readiness.es","question":"Safe?","predicates":["age.ge.18"]}`
	w := httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/sessions", bytes.NewReader([]byte(reqBody))))
	require.Equal(t, http.StatusOK, w.Code)

	var session struct {
		SessionID     string `json:"sessionId"`
		Nonce         string `json:"nonce"`
		VerifierDID   string `json:"verifierDid"`
		RequestObject string `json:"requestObject"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &session))
	assert.NotEmpty(t, session.RequestObject, "session must include signed requestObject")
	assert.Equal(t, verifierDID, session.VerifierDID)

	// Step 2: Fetch DID document
	w = httptest.NewRecorder()
	verifier.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/.well-known/did.json", nil))
	require.Equal(t, http.StatusOK, w.Code)

	var didDoc struct {
		ID                 string `json:"id"`
		VerificationMethod []struct {
			ID           string            `json:"id"`
			PublicKeyJwk map[string]string `json:"publicKeyJwk"`
		} `json:"verificationMethod"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &didDoc))
	assert.Equal(t, verifierDID, didDoc.ID)
	require.Len(t, didDoc.VerificationMethod, 1)
	assert.Equal(t, verifierDID+"#key-1", didDoc.VerificationMethod[0].ID)
	assert.Equal(t, "EC", didDoc.VerificationMethod[0].PublicKeyJwk["kty"])
	assert.Equal(t, "P-256", didDoc.VerificationMethod[0].PublicKeyJwk["crv"])

	// Step 3: Verify the requestObject JWT signature using the DID document key
	pubJWK := didDoc.VerificationMethod[0].PublicKeyJwk
	xBytes, err := base64.RawURLEncoding.DecodeString(pubJWK["x"])
	require.NoError(t, err)
	yBytes, err := base64.RawURLEncoding.DecodeString(pubJWK["y"])
	require.NoError(t, err)
	pubKey := &ecdsa.PublicKey{
		Curve: elliptic.P256(),
		X:     new(big.Int).SetBytes(xBytes),
		Y:     new(big.Int).SetBytes(yBytes),
	}

	ecJWK, err := jwk.FromRaw(pubKey)
	require.NoError(t, err)
	token, err := jwxjwt.Parse([]byte(session.RequestObject), jwxjwt.WithKey(jwa.ES256, ecJWK))
	require.NoError(t, err, "requestObject signature must verify against DID document key")

	// Step 4: Verify claims
	clientID, _ := token.Get("client_id")
	assert.Equal(t, verifierDID, clientID)
	nonce, _ := token.Get("nonce")
	assert.Equal(t, session.Nonce, nonce)
	state, _ := token.Get("state")
	assert.Equal(t, session.SessionID, state)

	presDef, _ := token.Get("presentation_definition")
	presDefMap := presDef.(map[string]interface{})
	assert.Equal(t, "pack.childcare.readiness.es", presDefMap["id"])

	clientMeta, _ := token.Get("client_metadata")
	clientMetaMap := clientMeta.(map[string]interface{})
	assert.Equal(t, "Safe?", clientMetaMap["question"])
}

// ── Test helpers ──

// buildIntegrationSDJWT builds a valid SD-JWT for integration testing.
// If cnfClaim is non-nil, includes holder binding for KB-JWT.
func buildIntegrationSDJWT(t *testing.T, key *ecdsa.PrivateKey, issuer string,
	sdClaims map[string]interface{}, cnfClaim map[string]interface{}) string {
	t.Helper()

	var disclosures []string
	var sdHashes []string
	for name, value := range sdClaims {
		salt := make([]byte, 16)
		_, err := rand.Read(salt)
		require.NoError(t, err)

		arr := []interface{}{base64.RawURLEncoding.EncodeToString(salt), name, value}
		jsonBytes, err := json.Marshal(arr)
		require.NoError(t, err)
		encoded := base64.RawURLEncoding.EncodeToString(jsonBytes)

		hash := sha256.Sum256([]byte(encoded))
		disclosures = append(disclosures, encoded)
		sdHashes = append(sdHashes, base64.RawURLEncoding.EncodeToString(hash[:]))
	}

	claims := gojwt.MapClaims{
		"iss":     issuer,
		"sub":     "did:example:holder",
		"iat":     time.Now().Unix(),
		"exp":     time.Now().Add(365 * 24 * time.Hour).Unix(),
		"_sd_alg": "sha-256",
		"_sd":     sdHashes,
	}
	if cnfClaim != nil {
		claims["cnf"] = cnfClaim
	}

	token := gojwt.NewWithClaims(gojwt.SigningMethodES256, claims)
	token.Header["typ"] = "vc+sd-jwt"
	issuerJWT, err := token.SignedString(key)
	require.NoError(t, err)

	result := issuerJWT
	for _, d := range disclosures {
		result += "~" + d
	}
	result += "~"
	return result
}

func buildIntegrationKBJWT(t *testing.T, key *ecdsa.PrivateKey, nonce, aud, sdHash string) string {
	t.Helper()
	claims := gojwt.MapClaims{
		"nonce":   nonce,
		"aud":     aud,
		"iat":     time.Now().Unix(),
		"sd_hash": sdHash,
	}
	token := gojwt.NewWithClaims(gojwt.SigningMethodES256, claims)
	token.Header["typ"] = "kb+jwt"
	signed, err := token.SignedString(key)
	require.NoError(t, err)
	return signed
}

func computeSDHash(sdJWTWithDisclosures string) string {
	hash := sha256.Sum256([]byte(sdJWTWithDisclosures))
	return base64.RawURLEncoding.EncodeToString(hash[:])
}

// newTestRelayServer creates an in-process relay server for testing.
func newTestRelayServer() http.Handler {
	cfg := common.ServerConfig{Name: "relay-test", Version: "0.0.1", Port: "0"}
	router := common.NewRouter(cfg)

	store := newRelayStore(5 * time.Minute)

	router.Post("/sessions", func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		sess := store.create(body)
		common.WriteJSON(w, r, http.StatusOK, map[string]string{
			"sessionId":   sess.id,
			"requestUri":  "/sessions/" + sess.id + "/request",
			"responseUri": "/sessions/" + sess.id + "/response",
		})
	})
	router.Get("/sessions/{id}/request", func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		sess := store.get(id)
		if sess == nil {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(sess.request)
	})
	router.Post("/sessions/{id}/response", func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		body, _ := io.ReadAll(r.Body)
		sess := store.get(id)
		if sess == nil {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		sess.response = body
		w.WriteHeader(http.StatusNoContent)
	})
	router.Get("/sessions/{id}/response", func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		sess := store.get(id)
		if sess == nil {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		if sess.response == nil {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(sess.response)
	})

	return router
}

// Minimal relay store for integration tests (no TTL eviction needed).
type relayTestSession struct {
	id       string
	request  []byte
	response []byte
}

type relayTestStore struct {
	sessions map[string]*relayTestSession
}

func newRelayStore(_ time.Duration) *relayTestStore {
	return &relayTestStore{sessions: make(map[string]*relayTestSession)}
}

func (s *relayTestStore) create(request []byte) *relayTestSession {
	id := generateTestID()
	sess := &relayTestSession{id: id, request: request}
	s.sessions[id] = sess
	return sess
}

func (s *relayTestStore) get(id string) *relayTestSession {
	return s.sessions[id]
}

func generateTestID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
