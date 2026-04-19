package main

import (
	"net/http"

	"github.com/cachet-id/cachet/services/common"
)

// APIKeyAuth returns middleware that validates the X-API-Key header.
func APIKeyAuth(expectedKey string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			key := r.Header.Get("X-API-Key")
			if key == "" {
				common.WriteError(w, r, http.StatusUnauthorized, "missing_api_key", "X-API-Key header is required")
				return
			}
			if key != expectedKey {
				common.WriteError(w, r, http.StatusUnauthorized, "invalid_api_key", "Invalid API key")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
