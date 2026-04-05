package eval

import (
	"crypto/ecdsa"
	"fmt"
)

// DIDResolver resolves a DID string to an ECDSA public key for signature verification.
type DIDResolver interface {
	Resolve(did string) (*ecdsa.PublicKey, error)
}

// StaticDIDResolver holds a fixed mapping of DID → public key.
// Suitable for MVP where there is a single known issuer.
type StaticDIDResolver struct {
	keys map[string]*ecdsa.PublicKey
}

// NewStaticDIDResolver creates a resolver with the given DID-to-key mappings.
func NewStaticDIDResolver(keys map[string]*ecdsa.PublicKey) *StaticDIDResolver {
	return &StaticDIDResolver{keys: keys}
}

func (r *StaticDIDResolver) Resolve(did string) (*ecdsa.PublicKey, error) {
	key, ok := r.keys[did]
	if !ok {
		return nil, fmt.Errorf("unknown issuer DID: %s", did)
	}
	return key, nil
}
