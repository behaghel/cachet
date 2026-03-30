package common

import (
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

// HealthHandler returns a handler that responds with structured health JSON.
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
