package credential

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

func testES256Key(t *testing.T) *ecdsa.PrivateKey {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	return key
}

func TestCreateDisclosure(t *testing.T) {
	d, err := CreateDisclosure("age", 34)
	require.NoError(t, err)

	// Encoded is base64url of JSON array [salt, name, value]
	decoded, err := base64.RawURLEncoding.DecodeString(d.Encoded)
	require.NoError(t, err)

	var arr []interface{}
	require.NoError(t, json.Unmarshal(decoded, &arr))
	assert.Len(t, arr, 3)
	assert.Equal(t, d.Salt, arr[0])
	assert.Equal(t, "age", arr[1])
	assert.Equal(t, float64(34), arr[2]) // JSON numbers are float64
}

func TestDisclosureHash(t *testing.T) {
	d, err := CreateDisclosure("nationality", "GB")
	require.NoError(t, err)

	// Hash should be base64url(sha256(encoded))
	hash := sha256.Sum256([]byte(d.Encoded))
	expected := base64.RawURLEncoding.EncodeToString(hash[:])
	assert.Equal(t, expected, d.Hash)
}

func TestDisclosureUniqueSalts(t *testing.T) {
	d1, _ := CreateDisclosure("age", 25)
	d2, _ := CreateDisclosure("age", 25)
	assert.NotEqual(t, d1.Salt, d2.Salt, "each disclosure should have a unique salt")
	assert.NotEqual(t, d1.Encoded, d2.Encoded)
}

func TestBuildSDJWT_Format(t *testing.T) {
	key := testES256Key(t)

	nonDisclosable := map[string]interface{}{
		"iss": "did:veriff:production",
		"sub": "did:example:holder",
		"iat": 1712188800,
		"exp": 4102444800,
	}
	sdClaims := map[string]interface{}{
		"age":         34,
		"nationality": "GB",
	}

	result, err := BuildSDJWT(nonDisclosable, sdClaims, key, "did:veriff:production#key-1")
	require.NoError(t, err)

	// Should have format: issuerJWT~disc1~disc2~
	parts := strings.Split(result, "~")
	assert.GreaterOrEqual(t, len(parts), 4) // jwt + 2 disclosures + trailing empty

	// First part is a JWT (3 dots)
	jwtParts := strings.Split(parts[0], ".")
	assert.Len(t, jwtParts, 3, "first element should be a JWS with 3 parts")

	// Last part is empty (trailing ~)
	assert.Empty(t, parts[len(parts)-1], "should have trailing ~ for KB-JWT slot")
}

func TestBuildSDJWT_IssuerJWTVerifiable(t *testing.T) {
	key := testES256Key(t)

	nonDisclosable := map[string]interface{}{
		"iss": "did:veriff:production",
		"iat": 1712188800,
		"exp": 4102444800,
	}
	sdClaims := map[string]interface{}{
		"age": 34,
	}

	result, err := BuildSDJWT(nonDisclosable, sdClaims, key, "")
	require.NoError(t, err)

	// Extract the issuer JWT
	issuerJWT := strings.Split(result, "~")[0]

	// Verify with the public key
	token, err := jwt.Parse(issuerJWT, func(token *jwt.Token) (interface{}, error) {
		assert.Equal(t, jwt.SigningMethodES256, token.Method)
		return &key.PublicKey, nil
	})
	require.NoError(t, err)
	assert.True(t, token.Valid)

	// Check claims
	claims := token.Claims.(jwt.MapClaims)
	assert.Equal(t, "did:veriff:production", claims["iss"])
	assert.Equal(t, "sha-256", claims["_sd_alg"])

	// _sd should contain exactly 1 hash
	sdArr, ok := claims["_sd"].([]interface{})
	require.True(t, ok)
	assert.Len(t, sdArr, 1)
}

func TestBuildSDJWT_DisclosureHashesMatchSD(t *testing.T) {
	key := testES256Key(t)

	sdClaims := map[string]interface{}{
		"age":          34,
		"nationality":  "GB",
		"documentType": "PASSPORT",
	}

	result, err := BuildSDJWT(map[string]interface{}{
		"iss": "did:veriff:production",
		"iat": 1712188800,
		"exp": 4102444800,
	}, sdClaims, key, "")
	require.NoError(t, err)

	// Parse the issuer JWT to get _sd hashes
	parts := strings.Split(result, "~")
	issuerJWT := parts[0]
	token, err := jwt.Parse(issuerJWT, func(token *jwt.Token) (interface{}, error) {
		return &key.PublicKey, nil
	})
	require.NoError(t, err)
	claims := token.Claims.(jwt.MapClaims)
	sdArr := claims["_sd"].([]interface{})
	sdHashes := make(map[string]bool)
	for _, h := range sdArr {
		sdHashes[h.(string)] = true
	}

	// Each disclosure's hash should appear in _sd
	disclosures := parts[1 : len(parts)-1] // skip JWT and trailing empty
	assert.Len(t, disclosures, 3)
	for _, disc := range disclosures {
		hash := sha256.Sum256([]byte(disc))
		hashEncoded := base64.RawURLEncoding.EncodeToString(hash[:])
		assert.True(t, sdHashes[hashEncoded], "disclosure hash %s not found in _sd array", hashEncoded)
	}
}

func TestBuildSDJWT_WrongKeyCannotVerify(t *testing.T) {
	key := testES256Key(t)
	wrongKey := testES256Key(t)

	result, err := BuildSDJWT(map[string]interface{}{
		"iss": "did:veriff:production",
		"iat": 1712188800,
		"exp": 4102444800,
	}, map[string]interface{}{"age": 34}, key, "")
	require.NoError(t, err)

	issuerJWT := strings.Split(result, "~")[0]
	_, err = jwt.Parse(issuerJWT, func(token *jwt.Token) (interface{}, error) {
		return &wrongKey.PublicKey, nil
	})
	assert.Error(t, err, "verification with wrong key should fail")
}

func TestBuildSDJWT_HeaderFields(t *testing.T) {
	key := testES256Key(t)

	result, err := BuildSDJWT(map[string]interface{}{
		"iss": "did:veriff:production",
		"iat": 1712188800,
		"exp": 4102444800,
	}, map[string]interface{}{"age": 34}, key, "did:veriff:production#key-1")
	require.NoError(t, err)

	issuerJWT := strings.Split(result, "~")[0]
	token, err := jwt.Parse(issuerJWT, func(token *jwt.Token) (interface{}, error) {
		return &key.PublicKey, nil
	})
	require.NoError(t, err)

	assert.Equal(t, "vc+sd-jwt", token.Header["typ"])
	assert.Equal(t, "did:veriff:production#key-1", token.Header["kid"])
	assert.Equal(t, "ES256", token.Header["alg"])
}

func TestPublicKeyToJWK_RoundTrip(t *testing.T) {
	key := testES256Key(t)
	jwk := PublicKeyToJWK(&key.PublicKey)

	assert.Equal(t, "EC", jwk["kty"])
	assert.Equal(t, "P-256", jwk["crv"])

	recovered, err := JWKToPublicKey(jwk)
	require.NoError(t, err)
	assert.Equal(t, key.PublicKey.X.Bytes(), recovered.X.Bytes())
	assert.Equal(t, key.PublicKey.Y.Bytes(), recovered.Y.Bytes())
}
