// Package jwe provides JWE encryption/decryption for end-to-end encrypted
// verifiable presentations. Uses X25519 ephemeral key agreement with
// ECDH-ES+A256KW and A256GCM per the verification protocol spec (Section 5.3).
package jwe

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"strings"

	"github.com/lestrrat-go/jwx/v2/jwa"
	jwxjwe "github.com/lestrrat-go/jwx/v2/jwe"
	"github.com/lestrrat-go/jwx/v2/jwk"
	"github.com/lestrrat-go/jwx/v2/x25519"
)

// GenerateEphemeralKeyPair creates an X25519 key pair for a single verification session.
// Returns the private key (for decryption) and the public key as base64url (for QR embedding).
func GenerateEphemeralKeyPair() (*ecdh.PrivateKey, string, error) {
	priv, err := ecdh.X25519().GenerateKey(rand.Reader)
	if err != nil {
		return nil, "", fmt.Errorf("generate X25519 key: %w", err)
	}
	pubB64 := base64.RawURLEncoding.EncodeToString(priv.PublicKey().Bytes())
	return priv, pubB64, nil
}

// Decrypt parses a JWE Compact Serialization string and decrypts it
// using the recipient's X25519 private key.
func Decrypt(compactJWE string, recipientKey *ecdh.PrivateKey) ([]byte, error) {
	privJWK, err := ecdhPrivateToJWK(recipientKey)
	if err != nil {
		return nil, fmt.Errorf("convert private key to JWK: %w", err)
	}

	plaintext, err := jwxjwe.Decrypt([]byte(compactJWE),
		jwxjwe.WithKey(jwa.ECDH_ES_A256KW, privJWK),
	)
	if err != nil {
		return nil, fmt.Errorf("decrypt JWE: %w", err)
	}
	return plaintext, nil
}

// Encrypt creates a JWE Compact Serialization string encrypting plaintext
// to the recipient's X25519 public key.
func Encrypt(plaintext []byte, recipientPubKey *ecdh.PublicKey) (string, error) {
	pubJWK, err := ecdhPublicToJWK(recipientPubKey)
	if err != nil {
		return "", fmt.Errorf("convert public key to JWK: %w", err)
	}

	encrypted, err := jwxjwe.Encrypt(plaintext,
		jwxjwe.WithKey(jwa.ECDH_ES_A256KW, pubJWK),
		jwxjwe.WithContentEncryption(jwa.A256GCM),
	)
	if err != nil {
		return "", fmt.Errorf("encrypt: %w", err)
	}
	return string(encrypted), nil
}

// IsJWE returns true if the string looks like a JWE Compact Serialization
// (5 base64url parts separated by dots) rather than an SD-JWT (contains ~).
func IsJWE(s string) bool {
	return strings.Count(s, ".") == 4 && !strings.Contains(s, "~")
}

// ecdhPublicToJWK converts a Go crypto/ecdh X25519 public key to a JWK
// using jwx's x25519.PublicKey type which it natively understands.
func ecdhPublicToJWK(pub *ecdh.PublicKey) (jwk.Key, error) {
	return jwk.FromRaw(x25519.PublicKey(pub.Bytes()))
}

// ecdhPrivateToJWK converts a Go crypto/ecdh X25519 private key to a JWK.
// jwx x25519.PrivateKey is seed(32) + public(32) = 64 bytes.
func ecdhPrivateToJWK(priv *ecdh.PrivateKey) (jwk.Key, error) {
	seed := priv.Bytes()            // 32-byte scalar
	pub := priv.PublicKey().Bytes() // 32-byte public
	combined := make([]byte, 0, 64)
	combined = append(combined, seed...)
	combined = append(combined, pub...)
	return jwk.FromRaw(x25519.PrivateKey(combined))
}
