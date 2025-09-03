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
	p := os.Getenv("PORT")
	if p == "" {
		p = "8090"
	}
	http.ListenAndServe(":"+p, r)
}
