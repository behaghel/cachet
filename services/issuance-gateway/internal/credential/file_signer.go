package credential

import (
	"crypto/ecdsa"
	"crypto/rand"
	"fmt"
	"math/big"
)

// FileSigner implements Signer using a local *ecdsa.PrivateKey.
// Used for local development, CI, and tests.
type FileSigner struct {
	key *ecdsa.PrivateKey
	kid string
}

// NewFileSigner wraps an existing ECDSA private key as a Signer.
func NewFileSigner(key *ecdsa.PrivateKey, kid string) *FileSigner {
	return &FileSigner{key: key, kid: kid}
}

func (s *FileSigner) Sign(digest []byte) ([]byte, error) {
	r, ss, err := ecdsa.Sign(rand.Reader, s.key, digest)
	if err != nil {
		return nil, fmt.Errorf("ecdsa sign: %w", err)
	}
	// Serialize r||s as 64 bytes (P-256: 32 bytes each, zero-padded)
	keyBytes := 32
	sig := make([]byte, 2*keyBytes)
	rBytes := r.Bytes()
	sBytes := ss.Bytes()
	copy(sig[keyBytes-len(rBytes):keyBytes], rBytes)
	copy(sig[2*keyBytes-len(sBytes):], sBytes)
	return sig, nil
}

func (s *FileSigner) PublicKey() (*ecdsa.PublicKey, error) {
	return &s.key.PublicKey, nil
}

func (s *FileSigner) KeyID() string {
	return s.kid
}

// parseRawSignature converts a raw r||s signature (64 bytes for P-256)
// back to (r, s) big.Ints. Used by tests for verification.
func parseRawSignature(sig []byte) (r, s *big.Int) {
	keyBytes := len(sig) / 2
	r = new(big.Int).SetBytes(sig[:keyBytes])
	s = new(big.Int).SetBytes(sig[keyBytes:])
	return r, s
}
