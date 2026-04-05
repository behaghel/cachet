package identity

import (
	"encoding/json"
	"testing"

	"github.com/lestrrat-go/jwx/v2/jwa"
	"github.com/lestrrat-go/jwx/v2/jwk"
	"github.com/lestrrat-go/jwx/v2/jws"
	"github.com/lestrrat-go/jwx/v2/jwt"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSignRequestObject(t *testing.T) {
	signer := NewDevSigner("did:web:verifier.test")

	claims := RequestObjectClaims{
		Nonce:      "test-nonce",
		State:      "session-123",
		PackID:     "pack.childcare.readiness.es",
		Question:   "Safe for my kids?",
		Predicates: []string{"age.ge.18", "identity.verified"},
	}

	signed, err := signer.SignRequestObject(claims)
	require.NoError(t, err)
	assert.NotEmpty(t, signed)

	// Verify the JWS signature using the signer's public key
	pubJWK, err := jwk.FromRaw(&signer.privateKey.PublicKey)
	require.NoError(t, err)

	token, err := jwt.Parse([]byte(signed), jwt.WithKey(jwa.ES256, pubJWK))
	require.NoError(t, err)

	// Check standard claims
	assert.Equal(t, "did:web:verifier.test", token.Issuer())
	assert.NotNil(t, token.IssuedAt())
	assert.NotNil(t, token.Expiration())

	// Check custom claims
	clientID, _ := token.Get("client_id")
	assert.Equal(t, "did:web:verifier.test", clientID)

	nonce, _ := token.Get("nonce")
	assert.Equal(t, "test-nonce", nonce)

	state, _ := token.Get("state")
	assert.Equal(t, "session-123", state)

	presDef, _ := token.Get("presentation_definition")
	presDefMap := presDef.(map[string]interface{})
	assert.Equal(t, "pack.childcare.readiness.es", presDefMap["id"])

	clientMeta, _ := token.Get("client_metadata")
	clientMetaMap := clientMeta.(map[string]interface{})
	assert.Equal(t, "Safe for my kids?", clientMetaMap["question"])
	assert.Equal(t, "Cachet Verifier", clientMetaMap["client_name"])
}

func TestSignRequestObject_VerifyTypHeader(t *testing.T) {
	signer := NewDevSigner("did:web:verifier.test")
	signed, err := signer.SignRequestObject(RequestObjectClaims{Nonce: "n", State: "s"})
	require.NoError(t, err)

	// Parse just the JWS to check headers (without verifying)
	msg, err := jws.Parse([]byte(signed))
	require.NoError(t, err)

	sigs := msg.Signatures()
	require.Len(t, sigs, 1)
	assert.Equal(t, "oauth-authz-req+jwt", sigs[0].ProtectedHeaders().Type())
	assert.Equal(t, "did:web:verifier.test#key-1", sigs[0].ProtectedHeaders().KeyID())
}

func TestPublicKeyJWK(t *testing.T) {
	signer := NewDevSigner("did:web:verifier.test")
	jwkMap := signer.PublicKeyJWK()

	assert.Equal(t, "EC", jwkMap["kty"])
	assert.Equal(t, "P-256", jwkMap["crv"])
	assert.NotEmpty(t, jwkMap["x"])
	assert.NotEmpty(t, jwkMap["y"])
	assert.Equal(t, "did:web:verifier.test#key-1", jwkMap["kid"])

	// Verify it's valid JSON when marshaled
	_, err := json.Marshal(jwkMap)
	assert.NoError(t, err)
}

func TestNewDevSigner_UniqueKeys(t *testing.T) {
	s1 := NewDevSigner("did:web:v1")
	s2 := NewDevSigner("did:web:v2")
	assert.NotEqual(t, s1.PublicKeyJWK()["x"], s2.PublicKeyJWK()["x"])
}
