package common

import (
	"net/http"
	"time"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

// AuditLog emits a structured audit log entry routable via "audit"=true.
// GCP Cloud Logging can sink these to BigQuery for retention.
func AuditLog(r *http.Request, action, resource, outcome string) {
	AuditLogWithActor(r, action, resource, outcome, "")
}

// AuditLogWithActor emits an audit entry with an explicit actor identifier.
func AuditLogWithActor(r *http.Request, action, resource, outcome, actor string) {
	evt := log.Ctx(r.Context()).Info().
		Bool("audit", true).
		Str("action", action).
		Str("resource", resource).
		Str("outcome", outcome).
		Str("timestamp", time.Now().UTC().Format(time.RFC3339))

	if actor != "" {
		evt = evt.Str("actor", actor)
	}
	evt.Msg("audit")
}

// AuditMiddleware wraps admin routes to automatically log every mutating request.
// Non-mutating methods (GET, HEAD, OPTIONS) are not logged.
func AuditMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Only audit mutating requests
		switch r.Method {
		case http.MethodGet, http.MethodHead, http.MethodOptions:
			next.ServeHTTP(w, r)
			return
		}

		// Wrap response writer to capture status code
		ww := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(ww, r)

		outcome := "success"
		if ww.status >= 400 {
			outcome = "failure"
		}

		log.Ctx(r.Context()).Info().
			Bool("audit", true).
			Str("action", r.Method+" "+r.URL.Path).
			Str("outcome", outcome).
			Int("status_code", ww.status).
			Str("timestamp", time.Now().UTC().Format(time.RFC3339)).
			Msg("audit")
	})
}

// statusWriter captures the HTTP status code from WriteHeader.
type statusWriter struct {
	http.ResponseWriter
	status      int
	wroteHeader bool
}

func (w *statusWriter) WriteHeader(code int) {
	if !w.wroteHeader {
		w.status = code
		w.wroteHeader = true
	}
	w.ResponseWriter.WriteHeader(code)
}

// Unwrap returns the underlying ResponseWriter for middleware compatibility.
func (w *statusWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

// interface assertion
var _ zerolog.LogObjectMarshaler = (*auditEntry)(nil)

// auditEntry is a helper for structured audit logging.
type auditEntry struct {
	Action   string
	Resource string
	Outcome  string
	Actor    string
}

func (e auditEntry) MarshalZerologObject(evt *zerolog.Event) {
	evt.Bool("audit", true).
		Str("action", e.Action).
		Str("resource", e.Resource).
		Str("outcome", e.Outcome)
	if e.Actor != "" {
		evt.Str("actor", e.Actor)
	}
}
