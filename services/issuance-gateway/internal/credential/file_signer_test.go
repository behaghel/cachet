package credential

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestFileSigner_SignAndVerify(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	signer := NewFileSigner(key, "test-kid")

	msg := []byte("test signing string")
	digest := sha256.Sum256(msg)

	sig, err := signer.Sign(digest[:])
	require.NoError(t, err)
	assert.Len(t, sig, 64, "P-256 raw signature should be 64 bytes")

	// Verify with standard ecdsa.Verify
	r, s := parseRawSignature(sig)
	assert.True(t, ecdsa.Verify(&key.PublicKey, digest[:], r, s))
}

func TestFileSigner_PublicKey(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	signer := NewFileSigner(key, "kid-1")

	pub, err := signer.PublicKey()
	require.NoError(t, err)
	assert.Equal(t, &key.PublicKey, pub)
}

func TestFileSigner_KeyID(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	signer := NewFileSigner(key, "did:web:api.cachet.vc#key-1")
	assert.Equal(t, "did:web:api.cachet.vc#key-1", signer.KeyID())
}

func TestFileSigner_DifferentSignaturesEachCall(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	signer := NewFileSigner(key, "kid")
	digest := sha256.Sum256([]byte("same message"))

	sig1, err := signer.Sign(digest[:])
	require.NoError(t, err)
	sig2, err := signer.Sign(digest[:])
	require.NoError(t, err)

	// ECDSA signatures are non-deterministic (different k each time)
	assert.NotEqual(t, sig1, sig2)

	// But both should verify
	r1, s1 := parseRawSignature(sig1)
	r2, s2 := parseRawSignature(sig2)
	assert.True(t, ecdsa.Verify(&key.PublicKey, digest[:], r1, s1))
	assert.True(t, ecdsa.Verify(&key.PublicKey, digest[:], r2, s2))
}
