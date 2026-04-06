package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"os"
	"time"

	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
	"github.com/cachet-id/cachet/services/verifier/internal/eval"
	"github.com/cachet-id/cachet/services/verifier/internal/identity"
)

func main() {
	common.InitLogging()

	cfg := common.ServerConfig{
		Name:    "verifier",
		Version: "0.1.0",
		Port:    "8081",
	}

	registryURL := os.Getenv("CACHET_REGISTRY_URL")
	if registryURL == "" {
		registryURL = "http://localhost:8082"
	}

	issuanceURL := os.Getenv("CACHET_ISSUANCE_URL")
	if issuanceURL == "" {
		issuanceURL = "http://localhost:8090"
	}

	verifierDID := os.Getenv("CACHET_VERIFIER_DID")
	if verifierDID == "" {
		verifierDID = "did:web:verifier.cachet.id"
	}

	// Discover issuer public key from issuance gateway JWKS endpoint
	resolver := fetchIssuerKeys(issuanceURL)

	// Identity signer for Request Objects (dev mode: ephemeral key)
	identitySigner := identity.NewDevSigner(verifierDID)
	log.Info().Str("did", verifierDID).Msg("using dev identity key for Request Object signing")

	server := NewServerWithConfig(VerifierConfig{
		Common:         cfg,
		RegistryURL:    registryURL,
		VerifierDID:    verifierDID,
		DIDResolver:    resolver,
		IdentitySigner: identitySigner,
	})
	common.ListenAndServe(server.Router(), cfg)
}

// fetchIssuerKeys fetches the JWKS from the issuance gateway and builds a DID resolver.
// Retries a few times on startup since the gateway may not be ready yet.
func fetchIssuerKeys(issuanceURL string) eval.DIDResolver {
	jwksURL := issuanceURL + "/.well-known/jwks.json"

	for attempt := 0; attempt < 10; attempt++ {
		req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, jwksURL, nil)
		if err != nil {
			log.Fatal().Err(err).Msg("failed to create JWKS request")
		}
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			log.Warn().Err(err).Int("attempt", attempt+1).Msg("waiting for issuance gateway JWKS")
			time.Sleep(2 * time.Second)
			continue
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			log.Warn().Int("status", resp.StatusCode).Int("attempt", attempt+1).Msg("JWKS not ready")
			time.Sleep(2 * time.Second)
			continue
		}

		var jwks struct {
			Keys []struct {
				Kty string `json:"kty"`
				Crv string `json:"crv"`
				X   string `json:"x"`
				Y   string `json:"y"`
				Kid string `json:"kid"`
			} `json:"keys"`
		}
		if err := json.NewDecoder(resp.Body).Decode(&jwks); err != nil {
			log.Error().Err(err).Msg("failed to decode JWKS")
			return nil
		}

		keys := make(map[string]*ecdsa.PublicKey)
		for _, k := range jwks.Keys {
			if k.Kty != "EC" || k.Crv != "P-256" {
				continue
			}
			xBytes, err := base64.RawURLEncoding.DecodeString(k.X)
			if err != nil {
				continue
			}
			yBytes, err := base64.RawURLEncoding.DecodeString(k.Y)
			if err != nil {
				continue
			}
			pub := &ecdsa.PublicKey{
				Curve: elliptic.P256(),
				X:     new(big.Int).SetBytes(xBytes),
				Y:     new(big.Int).SetBytes(yBytes),
			}
			// Extract issuer DID from kid: "did:veriff:production#key-1" → "did:veriff:production"
			issuerDID := k.Kid
			if idx := len(issuerDID) - 1; idx > 0 {
				for i := len(issuerDID) - 1; i >= 0; i-- {
					if issuerDID[i] == '#' {
						issuerDID = issuerDID[:i]
						break
					}
				}
			}
			keys[issuerDID] = pub
			log.Info().Str("kid", k.Kid).Str("issuer_did", issuerDID).Msg("loaded issuer key from JWKS")
		}

		if len(keys) > 0 {
			return eval.NewStaticDIDResolver(keys)
		}
		log.Warn().Msg("JWKS returned no usable keys")
		return nil
	}

	log.Warn().Msg("could not fetch issuer keys from issuance gateway — SD-JWT verification will fail")
	return nil
}
