package main

import (
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"os"
)

func main() {
	// Configure structured logging
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	if os.Getenv("ENVIRONMENT") == "development" {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr})
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8082"
	}

	server := NewServer()
	log.Info().Str("port", port).Msg("Starting registry service")
	if err := server.Start(":" + port); err != nil {
		log.Fatal().Err(err).Msg("Failed to start server")
	}
}
