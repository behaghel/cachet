package common

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/rs/zerolog/log"
)

// HealthResponse is the structured health check response.
type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
	Version string `json:"version"`
}

// ReadinessCheck is a function that reports whether a dependency is ready.
type ReadinessCheck func(ctx context.Context) error

// HealthHandler returns a liveness handler (always OK if process is up).
func HealthHandler(service, version string) http.HandlerFunc {
	resp := HealthResponse{
		Status:  "ok",
		Service: service,
		Version: version,
	}
	body, _ := json.Marshal(resp)

	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if _, err := w.Write(body); err != nil {
			log.Ctx(r.Context()).Error().Err(err).Msg("health write failed")
		}
	}
}

// ReadyHandler returns a readiness handler that checks all dependencies.
// If no checks are registered, it behaves like the liveness handler.
func ReadyHandler(service, version string, checks ...ReadinessCheck) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		for _, check := range checks {
			if err := check(r.Context()); err != nil {
				resp := HealthResponse{Status: "not_ready", Service: service, Version: version}
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusServiceUnavailable)
				json.NewEncoder(w).Encode(resp)
				return
			}
		}
		resp := HealthResponse{Status: "ok", Service: service, Version: version}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}
}
