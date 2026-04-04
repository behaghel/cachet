package credential

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"

	"github.com/golang-jwt/jwt/v5"
)

// Disclosure represents a single selectively disclosable claim in an SD-JWT.
type Disclosure struct {
	Salt      string      // random salt, base64url-encoded
	ClaimName string      // claim key
	Value     interface{} // claim value
	Encoded   string      // base64url(json([salt, name, value]))
	Hash      string      // base64url(sha256(Encoded))
}

// CreateDisclosure creates a disclosure for a single claim per SD-JWT spec (draft-13).
// Format: base64url(json([salt, claim_name, claim_value]))
func CreateDisclosure(claimName string, value interface{}) (Disclosure, error) {
	salt := make([]byte, 16) // 128 bits
	if _, err := rand.Read(salt); err != nil {
		return Disclosure{}, fmt.Errorf("generate salt: %w", err)
	}
	saltEncoded := base64.RawURLEncoding.EncodeToString(salt)

	arr := []interface{}{saltEncoded, claimName, value}
	jsonBytes, err := json.Marshal(arr)
	if err != nil {
		return Disclosure{}, fmt.Errorf("marshal disclosure: %w", err)
	}

	encoded := base64.RawURLEncoding.EncodeToString(jsonBytes)
	hash := sha256.Sum256([]byte(encoded))
	hashEncoded := base64.RawURLEncoding.EncodeToString(hash[:])

	return Disclosure{
		Salt:      saltEncoded,
		ClaimName: claimName,
		Value:     value,
		Encoded:   encoded,
		Hash:      hashEncoded,
	}, nil
}

// SDJWTClaims holds the non-disclosable JWT payload claims for an SD-JWT VC.
type SDJWTClaims struct {
	Issuer         string                 `json:"iss"`
	Subject        string                 `json:"sub"`
	IssuedAt       int64                  `json:"iat"`
	Expiration     int64                  `json:"exp"`
	SDAlgorithm    string                 `json:"_sd_alg"`
	SD             []string               `json:"_sd"`
	CNF            map[string]interface{} `json:"cnf,omitempty"`
	Status         map[string]interface{} `json:"status,omitempty"`
	CredentialType []string               `json:"vct,omitempty"`
}

// BuildSDJWT constructs a complete SD-JWT string with selective disclosures.
// Returns: issuerJWT~disclosure1~disclosure2~...~ (trailing ~ for KB-JWT slot)
func BuildSDJWT(nonDisclosable map[string]interface{}, selectiveDisclosureClaims map[string]interface{}, signingKey *ecdsa.PrivateKey, keyID string) (string, error) {
	// Create disclosures for each selectively disclosable claim
	var disclosures []Disclosure
	var sdHashes []string
	for name, value := range selectiveDisclosureClaims {
		d, err := CreateDisclosure(name, value)
		if err != nil {
			return "", fmt.Errorf("create disclosure for %s: %w", name, err)
		}
		disclosures = append(disclosures, d)
		sdHashes = append(sdHashes, d.Hash)
	}

	// Build the JWT payload: non-disclosable claims + _sd array + _sd_alg
	payload := make(map[string]interface{})
	for k, v := range nonDisclosable {
		payload[k] = v
	}
	payload["_sd"] = sdHashes
	payload["_sd_alg"] = "sha-256"

	// Sign as JWS with ES256
	token := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.MapClaims(payload))
	token.Header["typ"] = "vc+sd-jwt"
	if keyID != "" {
		token.Header["kid"] = keyID
	}

	issuerJWT, err := token.SignedString(signingKey)
	if err != nil {
		return "", fmt.Errorf("sign issuer JWT: %w", err)
	}

	// Concatenate: issuerJWT~disc1~disc2~...~
	result := issuerJWT
	for _, d := range disclosures {
		result += "~" + d.Encoded
	}
	result += "~" // trailing ~ for KB-JWT slot

	return result, nil
}

// PublicKeyToJWK converts an ECDSA public key to a JWK map (P-256/ES256).
func PublicKeyToJWK(pub *ecdsa.PublicKey) map[string]interface{} {
	return map[string]interface{}{
		"kty": "EC",
		"crv": "P-256",
		"x":   base64.RawURLEncoding.EncodeToString(pub.X.Bytes()),
		"y":   base64.RawURLEncoding.EncodeToString(pub.Y.Bytes()),
	}
}

// JWKToPublicKey converts a JWK map back to an ECDSA public key (P-256).
func JWKToPublicKey(jwk map[string]interface{}) (*ecdsa.PublicKey, error) {
	xStr, ok := jwk["x"].(string)
	if !ok {
		return nil, fmt.Errorf("missing or invalid x coordinate")
	}
	yStr, ok := jwk["y"].(string)
	if !ok {
		return nil, fmt.Errorf("missing or invalid y coordinate")
	}

	xBytes, err := base64.RawURLEncoding.DecodeString(xStr)
	if err != nil {
		return nil, fmt.Errorf("decode x: %w", err)
	}
	yBytes, err := base64.RawURLEncoding.DecodeString(yStr)
	if err != nil {
		return nil, fmt.Errorf("decode y: %w", err)
	}

	return &ecdsa.PublicKey{
		Curve: elliptic.P256(),
		X:     new(big.Int).SetBytes(xBytes),
		Y:     new(big.Int).SetBytes(yBytes),
	}, nil
}
