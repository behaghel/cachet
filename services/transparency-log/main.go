package main

import (
	"net/http"
	"os"
	"time"

	"github.com/behaghel/cachet/services/common/config"
	"github.com/go-chi/chi/v5"
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

	port := config.ResolvePort("PORT", cfg.Services.TransparencyLog.Port)

	r := chi.NewRouter()
	// Note: /healthz is reserved by Cloud Run infrastructure - use /health instead
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		if _, err := w.Write([]byte("ok")); err != nil {
			log.Error().Err(err).Msg("Failed to write health check response")
		}
	})
	log.Info().
		Str("env", env).
		Str("port", port).
		Msg("Starting transparency-log")

	server := &http.Server{
		Addr:         ":" + port,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	if err := server.ListenAndServe(); err != nil {
		log.Fatal().Err(err).Msg("Server failed to start")
	}
}
