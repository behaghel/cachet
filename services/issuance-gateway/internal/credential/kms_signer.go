package credential

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	"encoding/asn1"
	"encoding/pem"
	"fmt"
	"math/big"
	"sync"

	kms "cloud.google.com/go/kms/apiv1"
	"cloud.google.com/go/kms/apiv1/kmspb"
)

// KMSSigner implements Signer using GCP Cloud KMS AsymmetricSign.
// The private key never leaves the HSM — signing is done via API call.
type KMSSigner struct {
	client  *kms.KeyManagementClient
	keyName string // full KMS resource name (projects/.../cryptoKeyVersions/1)
	kid     string

	mu     sync.RWMutex
	pubKey *ecdsa.PublicKey // cached after first fetch
}

// NewKMSSigner creates a KMS-backed signer. The client and keyName must be
// valid; the public key is fetched lazily on first use.
func NewKMSSigner(client *kms.KeyManagementClient, keyName, kid string) *KMSSigner {
	return &KMSSigner{
		client:  client,
		keyName: keyName,
		kid:     kid,
	}
}

func (s *KMSSigner) Sign(digest []byte) ([]byte, error) {
	resp, err := s.client.AsymmetricSign(context.Background(), &kmspb.AsymmetricSignRequest{
		Name: s.keyName,
		Digest: &kmspb.Digest{
			Digest: &kmspb.Digest_Sha256{Sha256: digest},
		},
	})
	if err != nil {
		return nil, fmt.Errorf("kms asymmetric sign: %w", err)
	}
	// KMS returns DER-encoded ASN.1 signature; convert to raw r||s (64 bytes for P-256)
	return derToRaw(resp.Signature, 32)
}

func (s *KMSSigner) PublicKey() (*ecdsa.PublicKey, error) {
	s.mu.RLock()
	if s.pubKey != nil {
		defer s.mu.RUnlock()
		return s.pubKey, nil
	}
	s.mu.RUnlock()

	s.mu.Lock()
	defer s.mu.Unlock()
	// Double-check after acquiring write lock
	if s.pubKey != nil {
		return s.pubKey, nil
	}

	resp, err := s.client.GetPublicKey(context.Background(), &kmspb.GetPublicKeyRequest{
		Name: s.keyName,
	})
	if err != nil {
		return nil, fmt.Errorf("kms get public key: %w", err)
	}

	block, _ := pem.Decode([]byte(resp.Pem))
	if block == nil {
		return nil, fmt.Errorf("kms public key: failed to decode PEM")
	}
	pub, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("kms public key: parse PKIX: %w", err)
	}
	ecPub, ok := pub.(*ecdsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("kms public key: expected ECDSA, got %T", pub)
	}
	if ecPub.Curve != elliptic.P256() {
		return nil, fmt.Errorf("kms public key: expected P-256, got %s", ecPub.Curve.Params().Name)
	}

	s.pubKey = ecPub
	return s.pubKey, nil
}

func (s *KMSSigner) KeyID() string {
	return s.kid
}

// ecdsaSig is the ASN.1 structure for an ECDSA signature.
type ecdsaSig struct {
	R, S *big.Int
}

// derToRaw converts a DER-encoded ASN.1 ECDSA signature to raw r||s format.
// keyBytes is the size of each component (32 for P-256).
func derToRaw(der []byte, keyBytes int) ([]byte, error) {
	var sig ecdsaSig
	if _, err := asn1.Unmarshal(der, &sig); err != nil {
		return nil, fmt.Errorf("unmarshal DER signature: %w", err)
	}
	raw := make([]byte, 2*keyBytes)
	rBytes := sig.R.Bytes()
	sBytes := sig.S.Bytes()
	copy(raw[keyBytes-len(rBytes):keyBytes], rBytes)
	copy(raw[2*keyBytes-len(sBytes):], sBytes)
	return raw, nil
}
