package main

import (
	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog/log"
	"net/http"
	"os"
)

func main() {
	r := chi.NewRouter()
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		if _, err := w.Write([]byte("ok")); err != nil {
			log.Error().Err(err).Msg("Failed to write health check response")
		}
	})
	port := os.Getenv("PORT")
	if port == "" {
		port = "8090"
	}
	log.Info().Str("port", port).Msg("Starting connector-hub")
	if err := http.ListenAndServe(":"+port, r); err != nil {
		log.Fatal().Err(err).Msg("Server failed to start")
	}
}
