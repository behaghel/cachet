package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/issuance-gateway/internal/veriff"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoadOrGenerateIssuerKey_GeneratesNewKey(t *testing.T) {
	dir := t.TempDir()
	keyPath := filepath.Join(dir, "issuer-key.pem")
	t.Setenv("ISSUER_KEY_FILE", keyPath)
	t.Setenv("DEVENV_STATE", "") // ISSUER_KEY_FILE takes precedence

	key := loadOrGenerateIssuerKey()

	require.NotNil(t, key)
	assert.Equal(t, elliptic.P256(), key.Curve)

	// Key file should have been written
	_, err := os.Stat(keyPath)
	require.NoError(t, err, "key file should exist")
}

func TestLoadOrGenerateIssuerKey_LoadsExistingKey(t *testing.T) {
	dir := t.TempDir()
	keyPath := filepath.Join(dir, "issuer-key.pem")

	// Pre-generate and persist a key
	original, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	der, err := x509.MarshalECPrivateKey(original)
	require.NoError(t, err)
	pemData := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: der})
	require.NoError(t, os.WriteFile(keyPath, pemData, 0o600))

	t.Setenv("ISSUER_KEY_FILE", keyPath)
	t.Setenv("DEVENV_STATE", "")

	loaded := loadOrGenerateIssuerKey()

	// The loaded key must match the original
	assert.True(t, original.Equal(loaded), "loaded key must equal the original persisted key")
}

func TestLoadOrGenerateIssuerKey_SameKeyAcrossCalls(t *testing.T) {
	dir := t.TempDir()
	keyPath := filepath.Join(dir, "issuer-key.pem")
	t.Setenv("ISSUER_KEY_FILE", keyPath)
	t.Setenv("DEVENV_STATE", "")

	key1 := loadOrGenerateIssuerKey()
	key2 := loadOrGenerateIssuerKey()

	assert.True(t, key1.Equal(key2), "two consecutive loads must return the same key")
}

func TestLoadOrGenerateIssuerKey_FallsBackToDevenvState(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("ISSUER_KEY_FILE", "")
	t.Setenv("DEVENV_STATE", dir)

	key := loadOrGenerateIssuerKey()
	require.NotNil(t, key)

	expectedPath := filepath.Join(dir, "issuer-key.pem")
	_, err := os.Stat(expectedPath)
	require.NoError(t, err, "key should be persisted under DEVENV_STATE")
}

func TestLoadOrGenerateIssuerKey_EphemeralWhenNoEnvVars(t *testing.T) {
	t.Setenv("ISSUER_KEY_FILE", "")
	t.Setenv("DEVENV_STATE", "")

	key := loadOrGenerateIssuerKey()
	require.NotNil(t, key, "should generate an ephemeral key")
}

func TestLoadOrGenerateIssuerKey_RegeneratesOnCorruptFile(t *testing.T) {
	dir := t.TempDir()
	keyPath := filepath.Join(dir, "issuer-key.pem")
	require.NoError(t, os.WriteFile(keyPath, []byte("not a valid PEM"), 0o600))
	t.Setenv("ISSUER_KEY_FILE", keyPath)
	t.Setenv("DEVENV_STATE", "")

	key := loadOrGenerateIssuerKey()

	require.NotNil(t, key, "should generate a new key despite corrupt file")
	assert.Equal(t, elliptic.P256(), key.Curve)
}

// Integration test: issue an SD-JWT with a persisted key, "restart" (rebuild
// server from the same key file), and verify the JWKS public key matches.
func TestJWKS_StableAcrossRestart(t *testing.T) {
	dir := t.TempDir()
	keyPath := filepath.Join(dir, "issuer-key.pem")
	t.Setenv("ISSUER_KEY_FILE", keyPath)
	t.Setenv("DEVENV_STATE", "")

	// First "boot" — generates key and serves JWKS
	key1 := loadOrGenerateIssuerKey()
	s1 := NewServerWithConfig(testConfigWithIssuerKey(t, key1))
	jwks1 := fetchJWKS(t, s1)

	// Second "boot" — loads key from file and serves JWKS
	key2 := loadOrGenerateIssuerKey()
	s2 := NewServerWithConfig(testConfigWithIssuerKey(t, key2))
	jwks2 := fetchJWKS(t, s2)

	assert.Equal(t, jwks1, jwks2, "JWKS must be identical across restarts")
}

// ── helpers ──

func testConfigWithIssuerKey(t *testing.T, ecKey *ecdsa.PrivateKey) ServerConfig {
	t.Helper()
	rsaKey, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	return ServerConfig{
		Common:      common.ServerConfig{Name: "test", Version: "0.0.1", Port: "0"},
		SigningKey:  rsaKey,
		IssuerKey:   ecKey,
		IssuerKeyID: "did:veriff:production#key-1",
		Sessions:    veriff.NewInMemoryStore(),
	}
}

func fetchJWKS(t *testing.T, s *Server) string {
	t.Helper()
	w := httptest.NewRecorder()
	s.Router().ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/.well-known/jwks.json", nil))
	require.Equal(t, http.StatusOK, w.Code)
	// Normalize JSON for comparison
	var parsed interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &parsed))
	normalized, _ := json.Marshal(parsed)
	return string(normalized)
}
