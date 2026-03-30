package common

import (
	"encoding/json"
	"net/http"

	"github.com/rs/zerolog/log"
)

// ErrorResponse matches the Error schema from the OpenAPI spec.
type ErrorResponse struct {
	Error   string      `json:"error"`
	Message string      `json:"message"`
	Details interface{} `json:"details,omitempty"`
}

// WriteError sends a structured JSON error response.
func WriteError(w http.ResponseWriter, r *http.Request, status int, code, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	resp := ErrorResponse{Error: code, Message: message}
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to write error response")
	}
}

// WriteJSON sends a JSON response with the given status code.
func WriteJSON(w http.ResponseWriter, r *http.Request, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Ctx(r.Context()).Error().Err(err).Msg("failed to write JSON response")
	}
}
