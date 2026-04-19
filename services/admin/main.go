package main

import (
	"os"

	"github.com/rs/zerolog/log"

	"github.com/cachet-id/cachet/services/common"
)

func main() {
	common.InitLogging()

	apiKey := os.Getenv("ADMIN_API_KEY")
	if apiKey == "" {
		log.Fatal().Msg("ADMIN_API_KEY is required")
	}

	registryURL := os.Getenv("CACHET_REGISTRY_URL")
	if registryURL == "" {
		registryURL = "http://localhost:8082"
	}

	issuanceURL := os.Getenv("CACHET_ISSUANCE_URL")
	if issuanceURL == "" {
		issuanceURL = "http://localhost:8090"
	}

	relayURL := os.Getenv("CACHET_RELAY_URL")
	if relayURL == "" {
		relayURL = "http://localhost:8084"
	}

	verifierURL := os.Getenv("CACHET_VERIFIER_URL")
	if verifierURL == "" {
		verifierURL = "http://localhost:8081"
	}

	cfg := AdminConfig{
		Common: common.ServerConfig{
			Name:    "admin",
			Version: "0.1.0",
			Port:    "8091",
		},
		APIKey:      apiKey,
		RegistryURL: registryURL,
		IssuanceURL: issuanceURL,
		RelayURL:    relayURL,
		VerifierURL: verifierURL,
	}

	server := NewServer(cfg)
	common.ListenAndServe(server.Router(), cfg.Common)
}
