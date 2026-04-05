package eval

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// testIssuerKey generates an ES256 key pair for testing.
func testIssuerKey(t *testing.T) *ecdsa.PrivateKey {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	return key
}

// buildTestSDJWT builds a valid SD-JWT for testing purposes.
func buildTestSDJWT(t *testing.T, key *ecdsa.PrivateKey, issuer string, sdClaims map[string]interface{}) string {
	t.Helper()

	// Create disclosures
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

	// Build issuer JWT
	claims := jwt.MapClaims{
		"iss":     issuer,
		"sub":     "did:example:holder",
		"iat":     1712188800,
		"exp":     4102444800, // 2099
		"_sd_alg": "sha-256",
		"_sd":     sdHashes,
	}
	token := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	token.Header["typ"] = "vc+sd-jwt"

	issuerJWT, err := token.SignedString(key)
	require.NoError(t, err)

	// Concatenate: issuerJWT~disc1~disc2~...~
	result := issuerJWT
	for _, d := range disclosures {
		result += "~" + d
	}
	result += "~"
	return result
}

func TestParseSDJWT_ValidFormat(t *testing.T) {
	key := testIssuerKey(t)
	raw := buildTestSDJWT(t, key, "did:veriff:production", map[string]interface{}{
		"age":         float64(34),
		"nationality": "GB",
	})

	parsed, err := ParseSDJWT(raw)
	require.NoError(t, err)
	assert.NotNil(t, parsed.IssuerJWT)
	assert.Len(t, parsed.Disclosures, 2)
	assert.Empty(t, parsed.KBJWT)

	// Disclosures should have correct claim names
	claimNames := map[string]bool{}
	for _, d := range parsed.Disclosures {
		claimNames[d.ClaimName] = true
	}
	assert.True(t, claimNames["age"])
	assert.True(t, claimNames["nationality"])
}

func TestParseSDJWT_InvalidFormat(t *testing.T) {
	_, err := ParseSDJWT("not-a-valid-sdjwt")
	assert.Error(t, err)
}

func TestVerifySDJWT_ValidCredential(t *testing.T) {
	key := testIssuerKey(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &key.PublicKey,
	})

	raw := buildTestSDJWT(t, key, "did:veriff:production", map[string]interface{}{
		"age":         float64(34),
		"nationality": "GB",
	})

	verified, err := VerifySDJWT(raw, resolver)
	require.NoError(t, err)
	assert.Equal(t, "did:veriff:production", verified.Issuer)
	assert.Equal(t, "did:example:holder", verified.Subject)

	// Verified claims should contain the disclosed values
	assert.Equal(t, float64(34), verified.Claims["age"])
	assert.Equal(t, "GB", verified.Claims["nationality"])
}

func TestVerifySDJWT_WrongSigningKey(t *testing.T) {
	signingKey := testIssuerKey(t)
	wrongKey := testIssuerKey(t)

	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &wrongKey.PublicKey, // wrong key
	})

	raw := buildTestSDJWT(t, signingKey, "did:veriff:production", map[string]interface{}{
		"age": float64(34),
	})

	_, err := VerifySDJWT(raw, resolver)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "verify issuer signature")
}

func TestVerifySDJWT_UnknownIssuer(t *testing.T) {
	key := testIssuerKey(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &key.PublicKey,
	})

	raw := buildTestSDJWT(t, key, "did:unknown:attacker", map[string]interface{}{
		"age": float64(34),
	})

	_, err := VerifySDJWT(raw, resolver)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unknown issuer DID")
}

func TestVerifySDJWT_TamperedDisclosure(t *testing.T) {
	key := testIssuerKey(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &key.PublicKey,
	})

	raw := buildTestSDJWT(t, key, "did:veriff:production", map[string]interface{}{
		"age": float64(34),
	})

	// Replace the disclosure with a forged one (different value)
	parts := strings.Split(raw, "~")
	forgedArr := []interface{}{"forged-salt", "age", float64(99)}
	forgedJSON, _ := json.Marshal(forgedArr)
	forgedEncoded := base64.RawURLEncoding.EncodeToString(forgedJSON)
	parts[1] = forgedEncoded
	tampered := strings.Join(parts, "~")

	_, err := VerifySDJWT(tampered, resolver)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not found in _sd array")
}

func TestVerifySDJWT_ForgedIssuerField(t *testing.T) {
	// Attacker signs with their own key but claims to be did:veriff:production
	attackerKey := testIssuerKey(t)
	realKey := testIssuerKey(t)

	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &realKey.PublicKey, // real key
	})

	// Attacker builds SD-JWT claiming to be the real issuer but signed with attacker key
	raw := buildTestSDJWT(t, attackerKey, "did:veriff:production", map[string]interface{}{
		"age": float64(99),
	})

	_, err := VerifySDJWT(raw, resolver)
	assert.Error(t, err, "forged credential signed by attacker should be rejected")
	assert.Contains(t, err.Error(), "verify issuer signature")
}

func TestVerifySDJWT_WrongSDAlgorithm(t *testing.T) {
	key := testIssuerKey(t)
	resolver := NewStaticDIDResolver(map[string]*ecdsa.PublicKey{
		"did:veriff:production": &key.PublicKey,
	})

	// Build a JWT with wrong _sd_alg
	claims := jwt.MapClaims{
		"iss":     "did:veriff:production",
		"iat":     1712188800,
		"exp":     4102444800,
		"_sd_alg": "sha-512", // wrong algorithm
		"_sd":     []string{},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	issuerJWT, err := token.SignedString(key)
	require.NoError(t, err)

	raw := issuerJWT + "~"
	_, err = VerifySDJWT(raw, resolver)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unsupported _sd_alg")
}
