package common

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

// ServerConfig holds configuration for creating a service.
type ServerConfig struct {
	Name    string
	Version string
	Port    string // default port if PORT env var is not set
}

// NewRouter creates a chi router with the standard middleware stack.
func NewRouter(cfg ServerConfig) *chi.Mux {
	r := chi.NewRouter()
	r.Use(RequestIDMiddleware)
	r.Use(TracingMiddleware(cfg.Name))
	r.Use(RequestLoggerMiddleware)
	r.Use(chi.Middlewares{recoverMiddleware}...)
	r.Get("/health", HealthHandler(cfg.Name, cfg.Version))
	r.Get("/ready", ReadyHandler(cfg.Name, cfg.Version)) // add ReadinessChecks via ReadyHandler(name, ver, check1, check2...)
	r.Handle("/metrics", MetricsHandler())
	return r
}

// ListenAndServe starts the HTTP server with graceful shutdown on SIGTERM/SIGINT.
// If OTEL_EXPORTER_OTLP_ENDPOINT is set, tracing is automatically enabled.
func ListenAndServe(handler http.Handler, cfg ServerConfig) {
	ctx := context.Background()
	InitMeterProvider()
	otelShutdown := InitOTel(ctx, cfg.Name, cfg.Version)
	defer otelShutdown()

	port := os.Getenv("PORT")
	if port == "" {
		port = cfg.Port
	}

	srv := &http.Server{
		Addr:         ":" + port,
		Handler:      handler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Info().Str("service", cfg.Name).Str("port", port).Msg("starting")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal().Err(err).Msg("server failed")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGTERM, syscall.SIGINT)
	<-quit

	log.Info().Msg("shutting down")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Error().Err(err).Msg("forced shutdown")
	}
}

// InitLogging sets up zerolog with console writer in development.
func InitLogging() {
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	if os.Getenv("ENVIRONMENT") == "development" {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stderr})
	}
}

// recoverMiddleware recovers from panics and logs them.
func recoverMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				log.Ctx(r.Context()).Error().Interface("panic", err).Msg("recovered from panic")
				http.Error(w, "internal server error", http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}
