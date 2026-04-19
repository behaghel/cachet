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

	cfg := AdminConfig{
		Common: common.ServerConfig{
			Name:    "admin",
			Version: "0.1.0",
			Port:    "8091",
		},
		APIKey:      apiKey,
		RegistryURL: registryURL,
		IssuanceURL: issuanceURL,
	}

	server := NewServer(cfg)
	common.ListenAndServe(server.Router(), cfg.Common)
}
