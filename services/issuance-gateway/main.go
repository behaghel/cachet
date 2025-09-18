package main

import (
	"os"

	"github.com/behaghel/cachet/services/common/config"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func main() {
	cfg := config.MustLoad()

	env := os.Getenv("ENVIRONMENT")
	if env == "" {
		env = cfg.Environment
	}

	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	if env == "development" || env == "local" {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr})
	}

	port := config.ResolvePort("PORT", cfg.Services.IssuanceGateway.Port)

	server := NewServer()
	log.Info().
		Str("env", env).
		Str("port", port).
		Msg("Starting issuance gateway service")
	if err := server.Start(":" + port); err != nil {
		log.Fatal().Err(err).Msg("Failed to start server")
	}
}
