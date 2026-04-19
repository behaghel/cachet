package main

import (
	"github.com/cachet-id/cachet/services/common"
	"go.opentelemetry.io/otel/metric"
)

// Metrics instruments for the verifier service.
var (
	verificationsTotal   metric.Int64Counter
	verificationDuration metric.Float64Histogram
	packsRequested       metric.Int64Counter
	sessionsCreated      metric.Int64Counter
)

func init() {
	meter := common.Meter("verifier")

	verificationsTotal, _ = meter.Int64Counter("cachet.verifications.total",
		metric.WithDescription("Total verification attempts by outcome"),
		metric.WithUnit("{verification}"),
	)
	verificationDuration, _ = meter.Float64Histogram("cachet.verifications.duration_seconds",
		metric.WithDescription("Time from request to verification result"),
		metric.WithUnit("s"),
	)
	packsRequested, _ = meter.Int64Counter("cachet.packs.requested",
		metric.WithDescription("Pack fetch requests by pack ID"),
		metric.WithUnit("{request}"),
	)
	sessionsCreated, _ = meter.Int64Counter("cachet.sessions.created",
		metric.WithDescription("Verification sessions created"),
		metric.WithUnit("{session}"),
	)
}
