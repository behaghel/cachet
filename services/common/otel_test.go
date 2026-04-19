package common

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/metric"
)

func TestMetricsEndpoint(t *testing.T) {
	// Ensure MeterProvider is initialised (idempotent in tests).
	InitMeterProvider()

	// Record a counter via the global meter provider.
	meter := otel.Meter("test-service")
	counter, err := meter.Int64Counter("test_counter",
		metric.WithDescription("a test counter"),
	)
	if err != nil {
		t.Fatalf("creating counter: %v", err)
	}
	counter.Add(t.Context(), 1)

	// Serve /metrics via the handler.
	req := httptest.NewRequest(http.MethodGet, "/metrics", nil)
	rec := httptest.NewRecorder()
	MetricsHandler().ServeHTTP(rec, req)

	resp := rec.Result()
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "test_counter") {
		t.Errorf("expected test_counter in prometheus output, got:\n%s", body)
	}
}
