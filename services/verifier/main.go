package main

import (
	"os"

	"github.com/cachet-id/cachet/services/common"
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

	server := NewServer(cfg, registryURL)
	common.ListenAndServe(server.Router(), cfg)
}
