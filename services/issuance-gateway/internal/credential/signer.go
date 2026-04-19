package credential

import "crypto/ecdsa"

// Signer abstracts SD-JWT credential signing. Implementations:
//   - FileSigner: uses a local *ecdsa.PrivateKey (dev, CI, tests)
//   - KMSSigner:  uses GCP Cloud KMS AsymmetricSign (staging, production)
type Signer interface {
	// Sign signs the given SHA-256 digest and returns the raw ECDSA
	// signature as r||s (each 32 bytes, big-endian, zero-padded for P-256).
	Sign(digest []byte) ([]byte, error)

	// PublicKey returns the ECDSA P-256 public key for JWKS serving.
	PublicKey() (*ecdsa.PublicKey, error)

	// KeyID returns the kid value for JWT headers.
	KeyID() string
}
