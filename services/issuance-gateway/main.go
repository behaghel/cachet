package main

import (
	"os"

	"github.com/behaghel/cachet/services/common/config"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func main() {
	cfg := config.MustLoad()

	rawEnvOverride := os.Getenv("ENVIRONMENT")
	runtimeEnv := rawEnvOverride
	if runtimeEnv == "" {
		runtimeEnv = cfg.Environment
	}

	cachetEnv := os.Getenv("CACHET_ENV")
	if cachetEnv == "" {
		cachetEnv = cfg.Environment
	}

	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	if runtimeEnv == "development" || runtimeEnv == "local" {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr})
	}

	port := config.ResolvePort("PORT", cfg.Services.IssuanceGateway.Port)

	log.Info().
		Str("environment", cfg.Environment).
		Str("cachet_env", cachetEnv).
		Str("environment_override", rawEnvOverride).
		Str("runtime_env", runtimeEnv).
		Str("host", cfg.Services.IssuanceGateway.Host).
		Int("config_port", cfg.Services.IssuanceGateway.Port).
		Str("effective_port", port).
		Str("public_url", cfg.Services.IssuanceGateway.PublicURL).
		Msg("Issuance gateway main configuration loaded")

	server := NewServer()
	log.Info().
		Str("env", runtimeEnv).
		Str("port", port).
		Msg("Starting issuance gateway service")
	if err := server.Start(":" + port); err != nil {
		log.Fatal().Err(err).Msg("Failed to start server")
	}
}
