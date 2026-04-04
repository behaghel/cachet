package eval

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"math/big"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// KBJWTResult holds the verified claims from a Key Binding JWT.
type KBJWTResult struct {
	Nonce    string
	Aud      string
	SDHash   string
	IssuedAt time.Time
}

// VerifyKBJWT verifies a Key Binding JWT against the holder's public key from cnf.
// It checks: signature, typ header, sd_hash, and iat freshness (5 min max).
// Nonce and aud validation is deferred to the caller (Slice 4).
func VerifyKBJWT(kbjwtStr string, cnf map[string]interface{}, expectedSDHash string) (*KBJWTResult, error) {
	if kbjwtStr == "" {
		return nil, fmt.Errorf("KB-JWT is empty")
	}

	// Extract the holder's public key from cnf.jwk
	holderKey, err := extractCNFKey(cnf)
	if err != nil {
		return nil, fmt.Errorf("extract holder key from cnf: %w", err)
	}

	// Parse and verify the KB-JWT signature
	token, err := jwt.Parse(kbjwtStr, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodECDSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return holderKey, nil
	})
	if err != nil {
		return nil, fmt.Errorf("verify KB-JWT signature: %w", err)
	}

	// Check typ header
	typ, _ := token.Header["typ"].(string)
	if typ != "kb+jwt" {
		return nil, fmt.Errorf("invalid KB-JWT typ header: expected kb+jwt, got %q", typ)
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, fmt.Errorf("unexpected KB-JWT claims type")
	}

	// Verify sd_hash matches the SD-JWT content
	sdHash, _ := claims["sd_hash"].(string)
	if sdHash == "" {
		return nil, fmt.Errorf("missing sd_hash in KB-JWT")
	}
	if sdHash != expectedSDHash {
		return nil, fmt.Errorf("sd_hash mismatch: KB-JWT binds to different disclosure set")
	}

	// Check iat freshness (KB-JWT must be recent — max 5 minutes old)
	iatFloat, ok := claims["iat"].(float64)
	if !ok {
		return nil, fmt.Errorf("missing or invalid iat in KB-JWT")
	}
	issuedAt := time.Unix(int64(iatFloat), 0)
	if time.Since(issuedAt) > 5*time.Minute {
		return nil, fmt.Errorf("KB-JWT expired: issued %v ago", time.Since(issuedAt).Round(time.Second))
	}

	nonce, _ := claims["nonce"].(string)
	aud, _ := claims["aud"].(string)

	return &KBJWTResult{
		Nonce:    nonce,
		Aud:      aud,
		SDHash:   sdHash,
		IssuedAt: issuedAt,
	}, nil
}

// ComputeSDHash computes the sd_hash for KB-JWT binding: base64url(sha256(issuerJWT~disc1~disc2~...~))
func ComputeSDHash(sdJWTWithoutKBJWT string) string {
	hash := sha256.Sum256([]byte(sdJWTWithoutKBJWT))
	return base64.RawURLEncoding.EncodeToString(hash[:])
}

// extractCNFKey extracts the holder's ECDSA public key from the cnf claim.
// Expected format: cnf: { jwk: { kty: "EC", crv: "P-256", x: "...", y: "..." } }
func extractCNFKey(cnf map[string]interface{}) (*ecdsa.PublicKey, error) {
	if cnf == nil {
		return nil, fmt.Errorf("cnf claim is nil")
	}

	jwkRaw, ok := cnf["jwk"]
	if !ok {
		return nil, fmt.Errorf("cnf.jwk not found")
	}
	jwk, ok := jwkRaw.(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("cnf.jwk is not an object")
	}

	kty, _ := jwk["kty"].(string)
	if kty != "EC" {
		return nil, fmt.Errorf("unsupported key type: %s (expected EC)", kty)
	}
	crv, _ := jwk["crv"].(string)
	if crv != "P-256" {
		return nil, fmt.Errorf("unsupported curve: %s (expected P-256)", crv)
	}

	xStr, _ := jwk["x"].(string)
	yStr, _ := jwk["y"].(string)
	if xStr == "" || yStr == "" {
		return nil, fmt.Errorf("missing x or y coordinate in JWK")
	}

	xBytes, err := base64.RawURLEncoding.DecodeString(xStr)
	if err != nil {
		return nil, fmt.Errorf("decode x coordinate: %w", err)
	}
	yBytes, err := base64.RawURLEncoding.DecodeString(yStr)
	if err != nil {
		return nil, fmt.Errorf("decode y coordinate: %w", err)
	}

	return &ecdsa.PublicKey{
		Curve: elliptic.P256(),
		X:     new(big.Int).SetBytes(xBytes),
		Y:     new(big.Int).SetBytes(yBytes),
	}, nil
}
