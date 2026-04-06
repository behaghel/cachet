package jwe

import (
	"crypto/ecdh"
	"crypto/rand"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRoundTrip(t *testing.T) {
	priv, pubB64, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)
	assert.NotEmpty(t, pubB64)

	plaintext := []byte("eyJhbGciOiJFUzI1NiJ9.payload~disclosure1~kbjwt")

	compact, err := Encrypt(plaintext, priv.PublicKey())
	require.NoError(t, err)
	assert.True(t, IsJWE(compact), "encrypted output should look like JWE")

	decrypted, err := Decrypt(compact, priv)
	require.NoError(t, err)
	assert.Equal(t, plaintext, decrypted)
}

func TestDecrypt_WrongKey(t *testing.T) {
	priv1, _, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)
	priv2, _, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)

	compact, err := Encrypt([]byte("secret"), priv1.PublicKey())
	require.NoError(t, err)

	_, err = Decrypt(compact, priv2)
	assert.Error(t, err, "decryption with wrong key should fail")
}

func TestDecrypt_MalformedInput(t *testing.T) {
	priv, _, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)

	_, err = Decrypt("not-a-jwe", priv)
	assert.Error(t, err)

	_, err = Decrypt("a.b.c.d.e", priv)
	assert.Error(t, err)
}

func TestGenerateEphemeralKeyPair_Unique(t *testing.T) {
	_, pub1, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)
	_, pub2, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)
	assert.NotEqual(t, pub1, pub2, "ephemeral keys should be unique")
}

func TestGenerateEphemeralKeyPair_PublicKeyLength(t *testing.T) {
	priv, _, err := GenerateEphemeralKeyPair()
	require.NoError(t, err)
	assert.Len(t, priv.PublicKey().Bytes(), 32, "X25519 public key is 32 bytes")
}

func TestIsJWE(t *testing.T) {
	// JWE compact: 5 dot-separated parts
	assert.True(t, IsJWE("a.b.c.d.e"))
	// SD-JWT: contains ~
	assert.False(t, IsJWE("header.payload.sig~disclosure~kbjwt"))
	// Regular JWT: 3 dot-separated parts
	assert.False(t, IsJWE("header.payload.sig"))
}

func TestEncrypt_ReturnsCompactFormat(t *testing.T) {
	priv, err := ecdh.X25519().GenerateKey(rand.Reader)
	require.NoError(t, err)

	compact, err := Encrypt([]byte("test"), priv.PublicKey())
	require.NoError(t, err)

	// JWE compact has exactly 5 parts
	parts := 1
	for _, c := range compact {
		if c == '.' {
			parts++
		}
	}
	assert.Equal(t, 5, parts, "JWE compact serialization has 5 parts")
}
