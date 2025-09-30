package main

import (
	"encoding/json"
	"net/http"
	"os"
	"time"

	"github.com/behaghel/cachet/services/common/config"
	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

type submit struct {
	ReceiptHash string `json:"receiptHash"`
}

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

	r := chi.NewRouter()
	// Note: /healthz is reserved by Cloud Run infrastructure - use /health instead
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		if _, err := w.Write([]byte("ok")); err != nil {
			log.Error().Err(err).Msg("Failed to write health check response")
		}
	})
	r.Post("/receipts/hash", func(w http.ResponseWriter, r *http.Request) {
		var s submit
		if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
			log.Error().Err(err).Msg("Failed to decode request")
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		resp := map[string]any{"accepted": true, "hash": s.ReceiptHash, "anchored": false}
		w.Header().Set("Content-Type", "application/json")
		if err := json.NewEncoder(w).Encode(resp); err != nil {
			log.Error().Err(err).Msg("Failed to encode response")
		}
	})
	r.Get("/log/sth", func(w http.ResponseWriter, r *http.Request) {
		resp := map[string]any{"treeSize": 0, "rootHash": "", "timestamp": "2025-08-31T11:41:30Z"}
		w.Header().Set("Content-Type", "application/json")
		if err := json.NewEncoder(w).Encode(resp); err != nil {
			log.Error().Err(err).Msg("Failed to encode response")
		}
	})
	r.Get("/log/proof", func(w http.ResponseWriter, r *http.Request) {
		resp := map[string]any{"included": false}
		w.Header().Set("Content-Type", "application/json")
		if err := json.NewEncoder(w).Encode(resp); err != nil {
			log.Error().Err(err).Msg("Failed to encode response")
		}
	})
	port := config.ResolvePort("PORT", cfg.Services.ReceiptsLog.Port)
	log.Info().
		Str("environment", cfg.Environment).
		Str("cachet_env", cachetEnv).
		Str("environment_override", rawEnvOverride).
		Str("runtime_env", runtimeEnv).
		Str("host", cfg.Services.ReceiptsLog.Host).
		Int("config_port", cfg.Services.ReceiptsLog.Port).
		Str("effective_port", port).
		Str("public_url", cfg.Services.ReceiptsLog.PublicURL).
		Msg("Receipts-log configuration loaded")

	log.Info().
		Str("env", runtimeEnv).
		Str("port", port).
		Msg("Starting receipts-log")

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
