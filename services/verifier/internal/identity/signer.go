// Package identity handles the verifier's long-lived identity key
// for signing Request Objects (JWS) and serving DID documents.
package identity

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"time"

	"github.com/lestrrat-go/jwx/v2/jwa"
	"github.com/lestrrat-go/jwx/v2/jwk"
	"github.com/lestrrat-go/jwx/v2/jws"
	"github.com/lestrrat-go/jwx/v2/jwt"
)

// Signer signs Request Objects as JWS using the verifier's ES256 identity key.
type Signer struct {
	privateKey *ecdsa.PrivateKey
	kid        string // e.g., "did:web:verifier.cachet.id#key-1"
	clientID   string // e.g., "did:web:verifier.cachet.id"
}

// NewSigner creates a signer with the given identity key.
func NewSigner(key *ecdsa.PrivateKey, kid, clientID string) *Signer {
	return &Signer{privateKey: key, kid: kid, clientID: clientID}
}

// NewDevSigner generates an ephemeral identity key for development.
func NewDevSigner(verifierDID string) *Signer {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		panic("failed to generate dev identity key: " + err.Error())
	}
	return NewSigner(key, verifierDID+"#key-1", verifierDID)
}

// RequestObjectClaims holds the claims embedded in a signed Request Object.
type RequestObjectClaims struct {
	Nonce      string
	State      string // verification session ID
	PackID     string
	Question   string
	Predicates []string
}

// SignRequestObject creates a signed JWT Request Object (JWS, ES256).
func (s *Signer) SignRequestObject(claims RequestObjectClaims) (string, error) {
	now := time.Now()

	ecKey, err := jwk.FromRaw(s.privateKey)
	if err != nil {
		return "", fmt.Errorf("convert private key to JWK: %w", err)
	}
	if err := ecKey.Set(jwk.KeyIDKey, s.kid); err != nil {
		return "", fmt.Errorf("set kid: %w", err)
	}
	if err := ecKey.Set(jwk.AlgorithmKey, jwa.ES256); err != nil {
		return "", fmt.Errorf("set alg: %w", err)
	}

	token, err := jwt.NewBuilder().
		Issuer(s.clientID).
		Subject(s.clientID).
		IssuedAt(now).
		Expiration(now.Add(5*time.Minute)).
		Claim("client_id", s.clientID).
		Claim("client_id_scheme", "did").
		Claim("response_type", "vp_token").
		Claim("nonce", claims.Nonce).
		Claim("state", claims.State).
		Claim("presentation_definition", map[string]interface{}{
			"id": claims.PackID,
		}).
		Claim("client_metadata", map[string]interface{}{
			"client_name": "Cachet Verifier",
			"question":    claims.Question,
			"predicates":  claims.Predicates,
		}).
		Build()
	if err != nil {
		return "", fmt.Errorf("build token: %w", err)
	}

	hdrs := jws.NewHeaders()
	if err := hdrs.Set(jws.TypeKey, "oauth-authz-req+jwt"); err != nil {
		return "", fmt.Errorf("set typ: %w", err)
	}
	if err := hdrs.Set(jws.KeyIDKey, s.kid); err != nil {
		return "", fmt.Errorf("set kid header: %w", err)
	}

	signed, err := jwt.Sign(token, jwt.WithKey(jwa.ES256, ecKey, jws.WithProtectedHeaders(hdrs)))
	if err != nil {
		return "", fmt.Errorf("sign request object: %w", err)
	}
	return string(signed), nil
}

// PublicKeyJWK returns the verifier's public key as a JWK map (for DID document).
func (s *Signer) PublicKeyJWK() map[string]string {
	pub := s.privateKey.PublicKey
	return map[string]string{
		"kty": "EC",
		"crv": "P-256",
		"x":   base64.RawURLEncoding.EncodeToString(pub.X.Bytes()),
		"y":   base64.RawURLEncoding.EncodeToString(pub.Y.Bytes()),
		"kid": s.kid,
	}
}

// ClientID returns the verifier's DID.
func (s *Signer) ClientID() string { return s.clientID }
