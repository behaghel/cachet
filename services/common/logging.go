package common

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5/middleware"
	"github.com/rs/zerolog/log"
)

// RequestIDMiddleware injects a request ID into the context and response header.
func RequestIDMiddleware(next http.Handler) http.Handler {
	return middleware.RequestID(next)
}

// RequestLoggerMiddleware injects a zerolog logger with request_id into the
// request context and logs each request on completion.
func RequestLoggerMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		reqID := middleware.GetReqID(r.Context())

		logger := log.With().Str("request_id", reqID).Logger()
		r = r.WithContext(logger.WithContext(r.Context()))

		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)

		log.Info().
			Str("request_id", reqID).
			Str("method", r.Method).
			Str("path", r.URL.Path).
			Int("status", ww.Status()).
			Dur("duration", time.Since(start)).
			Msg("request")
	})
}
