package eval

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/golang-jwt/jwt/v5"
)

// ParsedSDJWT holds the components of a parsed SD-JWT.
type ParsedSDJWT struct {
	IssuerJWT    *jwt.Token
	RawIssuerJWT string
	Disclosures  []ParsedDisclosure
	KBJWT        string // empty until Slice 3
}

// ParsedDisclosure represents a decoded selective disclosure.
type ParsedDisclosure struct {
	Encoded   string // original base64url-encoded disclosure
	Salt      string
	ClaimName string
	Value     interface{}
	Hash      string // base64url(sha256(Encoded))
}

// VerifiedClaims contains the claims extracted from a verified SD-JWT.
type VerifiedClaims struct {
	Issuer      string
	Subject     string
	IssuedAt    int64
	Expiration  int64
	Claims      map[string]interface{} // merged non-disclosable + verified disclosures
	HolderBound bool                   // true if KB-JWT was verified
	KBJWTNonce  string                 // nonce from KB-JWT (checked in Slice 4)
	KBJWTAud    string                 // audience from KB-JWT (checked in Slice 4)
}

// ParseSDJWT splits an SD-JWT string into its components without verifying signatures.
func ParseSDJWT(raw string) (*ParsedSDJWT, error) {
	parts := strings.Split(raw, "~")
	if len(parts) < 2 {
		return nil, fmt.Errorf("invalid SD-JWT: expected at least issuer JWT and one delimiter")
	}

	issuerJWTStr := parts[0]

	// Parse the issuer JWT without validation (validation happens in VerifySDJWT)
	parser := jwt.NewParser(jwt.WithoutClaimsValidation())
	token, _, err := parser.ParseUnverified(issuerJWTStr, jwt.MapClaims{})
	if err != nil {
		return nil, fmt.Errorf("parse issuer JWT: %w", err)
	}

	parsed := &ParsedSDJWT{
		IssuerJWT:    token,
		RawIssuerJWT: issuerJWTStr,
	}

	// Parse disclosures (middle parts, between issuer JWT and optional KB-JWT)
	// Last part is either empty (trailing ~) or KB-JWT
	for i := 1; i < len(parts); i++ {
		if parts[i] == "" {
			continue // trailing ~ or empty segment
		}

		// Try to decode as a disclosure
		d, err := decodeDisclosure(parts[i])
		if err != nil {
			// If it looks like a JWT (3 dot-separated parts), treat as KB-JWT
			if strings.Count(parts[i], ".") == 2 {
				parsed.KBJWT = parts[i]
				continue
			}
			return nil, fmt.Errorf("decode disclosure at position %d: %w", i, err)
		}
		parsed.Disclosures = append(parsed.Disclosures, d)
	}

	return parsed, nil
}

// VerifySDJWT parses and cryptographically verifies an SD-JWT against a resolver.
// Steps: parse → resolve issuer DID → verify JWS → verify disclosure hashes → merge claims.
func VerifySDJWT(raw string, resolver DIDResolver) (*VerifiedClaims, error) {
	parsed, err := ParseSDJWT(raw)
	if err != nil {
		return nil, fmt.Errorf("parse: %w", err)
	}

	claims, ok := parsed.IssuerJWT.Claims.(jwt.MapClaims)
	if !ok {
		return nil, fmt.Errorf("unexpected claims type")
	}

	// Extract issuer DID
	issuer, _ := claims["iss"].(string)
	if issuer == "" {
		return nil, fmt.Errorf("missing iss claim")
	}

	// Resolve issuer DID to public key
	pubKey, err := resolver.Resolve(issuer)
	if err != nil {
		return nil, fmt.Errorf("resolve issuer DID %s: %w", issuer, err)
	}

	// Verify the issuer JWT signature
	_, err = jwt.Parse(parsed.RawIssuerJWT, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodECDSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return pubKey, nil
	})
	if err != nil {
		return nil, fmt.Errorf("verify issuer signature: %w", err)
	}

	// Verify _sd_alg is sha-256
	sdAlg, _ := claims["_sd_alg"].(string)
	if sdAlg != "sha-256" {
		return nil, fmt.Errorf("unsupported _sd_alg: %q (only sha-256 accepted)", sdAlg)
	}

	// Get the _sd array of hashes
	sdArr, _ := claims["_sd"].([]interface{})
	sdHashes := make(map[string]bool, len(sdArr))
	for _, h := range sdArr {
		if s, ok := h.(string); ok {
			sdHashes[s] = true
		}
	}

	// Verify each disclosure hash exists in _sd
	mergedClaims := make(map[string]interface{})
	for _, d := range parsed.Disclosures {
		if !sdHashes[d.Hash] {
			return nil, fmt.Errorf("disclosure hash %s for claim %q not found in _sd array", d.Hash, d.ClaimName)
		}
		mergedClaims[d.ClaimName] = d.Value
	}

	// Verify KB-JWT if present (holder binding)
	var kbResult *KBJWTResult
	if parsed.KBJWT != "" {
		cnf, _ := claims["cnf"].(map[string]interface{})
		if cnf == nil {
			return nil, fmt.Errorf("KB-JWT present but credential has no cnf claim for holder binding")
		}

		// Compute expected sd_hash: hash of everything before the KB-JWT
		// Format: issuerJWT~disc1~disc2~...~ (the trailing ~ is included)
		sdContent := parsed.RawIssuerJWT
		for _, d := range parsed.Disclosures {
			sdContent += "~" + d.Encoded
		}
		sdContent += "~"
		expectedSDHash := ComputeSDHash(sdContent)

		kbResult, err = VerifyKBJWT(parsed.KBJWT, cnf, expectedSDHash)
		if err != nil {
			return nil, fmt.Errorf("verify KB-JWT: %w", err)
		}
	}

	// Extract non-disclosable claims
	sub, _ := claims["sub"].(string)
	iat, _ := claims["iat"].(float64)
	exp, _ := claims["exp"].(float64)

	result := &VerifiedClaims{
		Issuer:     issuer,
		Subject:    sub,
		IssuedAt:   int64(iat),
		Expiration: int64(exp),
		Claims:     mergedClaims,
	}

	if kbResult != nil {
		result.HolderBound = true
		result.KBJWTNonce = kbResult.Nonce
		result.KBJWTAud = kbResult.Aud
	}

	return result, nil
}

// decodeDisclosure decodes a base64url-encoded disclosure into its components.
func decodeDisclosure(encoded string) (ParsedDisclosure, error) {
	jsonBytes, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return ParsedDisclosure{}, fmt.Errorf("base64url decode: %w", err)
	}

	var arr []interface{}
	if err := json.Unmarshal(jsonBytes, &arr); err != nil {
		return ParsedDisclosure{}, fmt.Errorf("unmarshal disclosure JSON: %w", err)
	}
	if len(arr) != 3 {
		return ParsedDisclosure{}, fmt.Errorf("expected 3 elements [salt, name, value], got %d", len(arr))
	}

	salt, ok := arr[0].(string)
	if !ok {
		return ParsedDisclosure{}, fmt.Errorf("salt is not a string")
	}
	name, ok := arr[1].(string)
	if !ok {
		return ParsedDisclosure{}, fmt.Errorf("claim name is not a string")
	}

	hash := sha256.Sum256([]byte(encoded))
	hashEncoded := base64.RawURLEncoding.EncodeToString(hash[:])

	return ParsedDisclosure{
		Encoded:   encoded,
		Salt:      salt,
		ClaimName: name,
		Value:     arr[2],
		Hash:      hashEncoded,
	}, nil
}
