package main

import (
	"encoding/json"
	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"
	"net/http"
	"os"
)

type submit struct {
	ReceiptHash string `json:"receiptHash"`
}

func main() {
	r := chi.NewRouter()
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
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
	port := os.Getenv("PORT")
	if port == "" {
		port = "8083"
	}
	log.Info().Str("port", port).Msg("Starting receipts-log")
	if err := http.ListenAndServe(":"+port, r); err != nil {
		log.Fatal().Err(err).Msg("Server failed to start")
	}
}
