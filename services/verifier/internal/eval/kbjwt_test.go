package eval

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func holderKeyAndCNF(t *testing.T) (*ecdsa.PrivateKey, map[string]interface{}) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	cnf := map[string]interface{}{
		"jwk": map[string]interface{}{
			"kty": "EC",
			"crv": "P-256",
			"x":   base64.RawURLEncoding.EncodeToString(key.PublicKey.X.Bytes()),
			"y":   base64.RawURLEncoding.EncodeToString(key.PublicKey.Y.Bytes()),
		},
	}
	return key, cnf
}

func buildTestKBJWT(t *testing.T, key *ecdsa.PrivateKey, nonce, aud, sdHash string, iat time.Time) string {
	t.Helper()
	claims := jwt.MapClaims{
		"nonce":   nonce,
		"aud":     aud,
		"iat":     iat.Unix(),
		"sd_hash": sdHash,
	}
	token := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	token.Header["typ"] = "kb+jwt"
	signed, err := token.SignedString(key)
	require.NoError(t, err)
	return signed
}

func TestVerifyKBJWT_Valid(t *testing.T) {
	holderKey, cnf := holderKeyAndCNF(t)
	sdHash := ComputeSDHash("test-sd-content~")
	kbjwt := buildTestKBJWT(t, holderKey, "test-nonce", "did:web:verifier.example", sdHash, time.Now())

	result, err := VerifyKBJWT(kbjwt, cnf, sdHash)
	require.NoError(t, err)
	assert.Equal(t, "test-nonce", result.Nonce)
	assert.Equal(t, "did:web:verifier.example", result.Aud)
	assert.Equal(t, sdHash, result.SDHash)
}

func TestVerifyKBJWT_WrongHolderKey(t *testing.T) {
	holderKey, _ := holderKeyAndCNF(t)
	_, wrongCNF := holderKeyAndCNF(t)

	sdHash := ComputeSDHash("test-sd-content~")
	kbjwt := buildTestKBJWT(t, holderKey, "n", "a", sdHash, time.Now())

	_, err := VerifyKBJWT(kbjwt, wrongCNF, sdHash)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "verify KB-JWT signature")
}

func TestVerifyKBJWT_WrongSDHash(t *testing.T) {
	holderKey, cnf := holderKeyAndCNF(t)
	kbjwt := buildTestKBJWT(t, holderKey, "n", "a", "wrong-hash", time.Now())

	_, err := VerifyKBJWT(kbjwt, cnf, "correct-hash")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "sd_hash mismatch")
}

func TestVerifyKBJWT_Expired(t *testing.T) {
	holderKey, cnf := holderKeyAndCNF(t)
	sdHash := ComputeSDHash("test~")
	kbjwt := buildTestKBJWT(t, holderKey, "n", "a", sdHash, time.Now().Add(-10*time.Minute))

	_, err := VerifyKBJWT(kbjwt, cnf, sdHash)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "KB-JWT expired")
}

func TestVerifyKBJWT_MissingCNF(t *testing.T) {
	holderKey, _ := holderKeyAndCNF(t)
	sdHash := ComputeSDHash("test~")
	kbjwt := buildTestKBJWT(t, holderKey, "n", "a", sdHash, time.Now())

	_, err := VerifyKBJWT(kbjwt, nil, sdHash)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "cnf claim is nil")
}

func TestVerifyKBJWT_WrongTypHeader(t *testing.T) {
	holderKey, cnf := holderKeyAndCNF(t)
	sdHash := ComputeSDHash("test~")

	claims := jwt.MapClaims{
		"nonce":   "n",
		"aud":     "a",
		"iat":     time.Now().Unix(),
		"sd_hash": sdHash,
	}
	token := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	token.Header["typ"] = "jwt" // wrong
	signed, err := token.SignedString(holderKey)
	require.NoError(t, err)

	_, err = VerifyKBJWT(signed, cnf, sdHash)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid KB-JWT typ header")
}

// End-to-end: SD-JWT with KB-JWT verified through VerifySDJWT
func TestVerifySDJWT_WithKBJWT(t *testing.T) {
	issuerKey := testIssuerKey(t)
	holderKey, _ := holderKeyAndCNF(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &issuerKey.PublicKey,
	})

	cnfClaim := map[string]interface{}{
		"jwk": map[string]interface{}{
			"kty": "EC",
			"crv": "P-256",
			"x":   base64.RawURLEncoding.EncodeToString(holderKey.PublicKey.X.Bytes()),
			"y":   base64.RawURLEncoding.EncodeToString(holderKey.PublicKey.Y.Bytes()),
		},
	}
	raw := buildTestSDJWTWithCNF(t, issuerKey, "did:veriff:production", map[string]interface{}{"age": float64(34)}, cnfClaim)

	// Compute sd_hash for the content before KB-JWT
	sdContent := raw // already has trailing ~
	sdHash := ComputeSDHash(sdContent)

	// Build KB-JWT signed by holder
	kbjwt := buildTestKBJWT(t, holderKey, "test-nonce", "did:web:verifier", sdHash, time.Now())
	fullPresentation := raw + kbjwt

	verified, err := VerifySDJWT(fullPresentation, resolver)
	require.NoError(t, err)
	assert.True(t, verified.HolderBound)
	assert.Equal(t, "test-nonce", verified.KBJWTNonce)
	assert.Equal(t, "did:web:verifier", verified.KBJWTAud)
	assert.Equal(t, float64(34), verified.Claims["age"])
}

func TestVerifySDJWT_WithKBJWT_WrongHolderKey(t *testing.T) {
	issuerKey := testIssuerKey(t)
	holderKey, _ := holderKeyAndCNF(t)
	attackerKey := testIssuerKey(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &issuerKey.PublicKey,
	})

	cnfClaim := map[string]interface{}{
		"jwk": map[string]interface{}{
			"kty": "EC",
			"crv": "P-256",
			"x":   base64.RawURLEncoding.EncodeToString(holderKey.PublicKey.X.Bytes()),
			"y":   base64.RawURLEncoding.EncodeToString(holderKey.PublicKey.Y.Bytes()),
		},
	}
	raw := buildTestSDJWTWithCNF(t, issuerKey, "did:veriff:production", map[string]interface{}{"age": float64(34)}, cnfClaim)
	sdHash := ComputeSDHash(raw)

	// Attacker signs KB-JWT with their own key
	kbjwt := buildTestKBJWT(t, attackerKey, "n", "a", sdHash, time.Now())
	fullPresentation := raw + kbjwt

	_, err := VerifySDJWT(fullPresentation, resolver)
	assert.Error(t, err, "KB-JWT signed by attacker should be rejected")
	assert.Contains(t, err.Error(), "verify KB-JWT")
}

// Helper: builds SD-JWT with cnf claim for holder binding tests
func buildTestSDJWTWithCNF(t *testing.T, key *ecdsa.PrivateKey, issuer string, sdClaims, cnfClaim map[string]interface{}) string {
	t.Helper()

	var disclosures []string
	var sdHashes []string
	for name, value := range sdClaims {
		salt := make([]byte, 16)
		_, err := rand.Read(salt)
		require.NoError(t, err)
		saltEncoded := base64.RawURLEncoding.EncodeToString(salt)

		arr := []interface{}{saltEncoded, name, value}
		jsonBytes, err := json.Marshal(arr)
		require.NoError(t, err)
		encoded := base64.RawURLEncoding.EncodeToString(jsonBytes)

		hash := sha256.Sum256([]byte(encoded))
		hashEncoded := base64.RawURLEncoding.EncodeToString(hash[:])
		disclosures = append(disclosures, encoded)
		sdHashes = append(sdHashes, hashEncoded)
	}

	claims := jwt.MapClaims{
		"iss":     issuer,
		"sub":     "did:example:holder",
		"iat":     1712188800,
		"exp":     4102444800,
		"_sd_alg": "sha-256",
		"_sd":     sdHashes,
		"cnf":     cnfClaim,
	}
	token := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
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
