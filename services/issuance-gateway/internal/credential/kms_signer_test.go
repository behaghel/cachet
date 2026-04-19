package credential

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/asn1"
	"encoding/pem"
	"math/big"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDerToRaw_KnownVector(t *testing.T) {
	// Create a known signature with a real key, encode as DER, then verify round-trip
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	digest := sha256.Sum256([]byte("test data"))
	r, s, err := ecdsa.Sign(rand.Reader, key, digest[:])
	require.NoError(t, err)

	// Encode as DER (ASN.1) — this is what KMS returns
	der, err := asn1.Marshal(ecdsaSig{R: r, S: s})
	require.NoError(t, err)

	// Convert to raw
	raw, err := derToRaw(der, 32)
	require.NoError(t, err)
	assert.Len(t, raw, 64)

	// Verify the raw signature matches original r, s
	rRecovered, sRecovered := parseRawSignature(raw)
	assert.Equal(t, r, rRecovered)
	assert.Equal(t, s, sRecovered)

	// Verify with ecdsa.Verify
	assert.True(t, ecdsa.Verify(&key.PublicKey, digest[:], rRecovered, sRecovered))
}

func TestDerToRaw_SmallComponents(t *testing.T) {
	// r and s with fewer than 32 bytes should be zero-padded
	r := big.NewInt(42)  // 1 byte
	s := big.NewInt(255) // 1 byte

	der, err := asn1.Marshal(ecdsaSig{R: r, S: s})
	require.NoError(t, err)

	raw, err := derToRaw(der, 32)
	require.NoError(t, err)
	assert.Len(t, raw, 64)

	// First 31 bytes of r should be zero
	for i := 0; i < 31; i++ {
		assert.Equal(t, byte(0), raw[i], "r byte %d should be zero-padded", i)
	}
	assert.Equal(t, byte(42), raw[31])

	// First 31 bytes of s should be zero
	for i := 32; i < 63; i++ {
		assert.Equal(t, byte(0), raw[i], "s byte %d should be zero-padded", i)
	}
	assert.Equal(t, byte(255), raw[63])
}

func TestDerToRaw_InvalidDER(t *testing.T) {
	_, err := derToRaw([]byte("not valid DER"), 32)
	assert.Error(t, err)
}

func TestKMSSigner_PublicKeyParsing(t *testing.T) {
	// Verify that the PEM parsing logic in PublicKey() handles standard PKIX format
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	// Encode as PKIX PEM (same format KMS returns)
	der, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	require.NoError(t, err)
	pemBytes := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der})

	// Parse it back (simulating what PublicKey() does internally)
	block, _ := pem.Decode(pemBytes)
	require.NotNil(t, block)
	pub, err := x509.ParsePKIXPublicKey(block.Bytes)
	require.NoError(t, err)
	ecPub, ok := pub.(*ecdsa.PublicKey)
	require.True(t, ok)
	assert.Equal(t, elliptic.P256(), ecPub.Curve)
	assert.Equal(t, key.X, ecPub.X)
	assert.Equal(t, key.Y, ecPub.Y)
}
